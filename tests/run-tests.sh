#!/usr/bin/env bash
# Runs the test suite and measures how much of the shop's JSP code it exercised.
#
#   docker compose --profile test run --rm tests
#
# 1. Reset the JaCoCo agent inside Tomcat (so manual browsing doesn't count).
# 2. Run the JUnit tests (mvn test).
# 3. Dump coverage from Tomcat and map it back to .jsp source lines.
# 4. Fail if any test failed, or if JSP line coverage is below COVERAGE_MIN (%).
#
# Reports (open on your Mac):
#   tests/target/coverage/jsp-lines.txt              coverage per .jsp line, misses listed
#   tests/target/coverage/generated-java/index.html  raw JaCoCo view of the generated servlets
set -uo pipefail

JACOCO_VERSION=0.8.12
JACOCO_HOST=${JACOCO_HOST:-}
JACOCO_PORT=${JACOCO_PORT:-6300}
COVERAGE_MIN=${COVERAGE_MIN:-90}
PROJECT_DIR=${PROJECT_DIR:-..}
WORK_DIR="$PROJECT_DIR/work/Catalina/localhost/shop"
OUT=target/jacoco
REPORT=target/coverage
CLI="$OUT/org.jacoco.cli-$JACOCO_VERSION-nodeps.jar"

rm -rf "$REPORT"
mkdir -p "$OUT" "$REPORT"

coverage=on
if [ -z "$JACOCO_HOST" ]; then
  echo "JACOCO_HOST not set: running tests without coverage."
  coverage=off
fi

if [ "$coverage" = on ]; then
  # JaCoCo command-line tool (cached in the maven-cache volume after the first run).
  if [ ! -f "$CLI" ]; then
    mvn -q -ntp dependency:copy \
      -Dartifact="org.jacoco:org.jacoco.cli:$JACOCO_VERSION:jar:nodeps" \
      -DoutputDirectory="$OUT" || { echo "Could not download the JaCoCo CLI" >&2; exit 2; }
  fi
  # Zero the counters. --retry waits for Tomcat to come up.
  if ! java -jar "$CLI" dump --address "$JACOCO_HOST" --port "$JACOCO_PORT" \
         --destfile "$OUT/discard.exec" --reset --retry 30 --quiet; then
    echo "Coverage agent not reachable at $JACOCO_HOST:$JACOCO_PORT." >&2
    echo "Rebuild the app so it has the agent: docker compose up -d --build" >&2
    exit 2
  fi
fi

# ---- Tests ----------------------------------------------------------------
mvn -B -ntp test
tests_status=$?

[ "$coverage" = on ] || exit $tests_status

# ---- Coverage -------------------------------------------------------------
java -jar "$CLI" dump --address "$JACOCO_HOST" --port "$JACOCO_PORT" \
  --destfile "$OUT/jsp.exec" --quiet || { echo "Coverage dump failed" >&2; exit 2; }

# Only report on JSPs that still exist in shop/ (Tomcat keeps classes for deleted pages).
classfiles=()
missing=()
while IFS= read -r jsp; do
  rel=${jsp#"$PROJECT_DIR/shop/"}
  dir=$(dirname "$rel"); base=$(basename "$rel" .jsp)
  [ "$dir" = "." ] && dir="" || dir="$dir/"
  cls="$WORK_DIR/org/apache/jsp/${dir}${base}_jsp.class"
  if [ -f "$cls" ]; then classfiles+=(--classfiles "$cls"); else missing+=("$rel"); fi
done < <(find "$PROJECT_DIR/shop" -name '*.jsp' | sort)

java -jar "$CLI" report "$OUT/jsp.exec" "${classfiles[@]}" \
  --sourcefiles "$WORK_DIR" --name "Ski Store JSPs (generated Java)" \
  --html "$REPORT/generated-java" --xml "$REPORT/jacoco.xml" --quiet \
  || { echo "Coverage report failed" >&2; exit 2; }

# Map generated-Java coverage back to .jsp lines (see tools/JspCoverage.java).
echo
java tools/JspCoverage.java "$REPORT/jacoco.xml" "$WORK_DIR/org/apache/jsp" \
  "$PROJECT_DIR/shop" "$COVERAGE_MIN" "$REPORT/jsp-lines.txt"
coverage_status=$?

status=$tests_status
[ $coverage_status -ne 0 ] && status=1
if [ ${#missing[@]} -gt 0 ]; then
  echo "FAIL: these pages were never loaded by any test: ${missing[*]}" >&2
  status=1
fi
[ $tests_status -ne 0 ] && echo "FAIL: some tests failed (see above)." >&2
[ $status -eq 0 ] && echo "PASS: all tests passed and coverage meets the minimum."
exit $status
