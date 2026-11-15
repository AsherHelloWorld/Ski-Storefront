IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = 'orders')
BEGIN
  CREATE DATABASE orders;
END;
go

USE orders;
go

DROP TABLE IF EXISTS review;
DROP TABLE IF EXISTS shipment;
DROP TABLE IF EXISTS productinventory;
DROP TABLE IF EXISTS warehouse;
DROP TABLE IF EXISTS orderproduct;
DROP TABLE IF EXISTS incart;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS ordersummary;
DROP TABLE IF EXISTS paymentmethod;
DROP TABLE IF EXISTS customer;
go

CREATE TABLE customer (
    customerId          INT IDENTITY,
    firstName           VARCHAR(40),
    lastName            VARCHAR(40),
    email               VARCHAR(50),
    phonenum            VARCHAR(20),
    address             VARCHAR(50),
    city                VARCHAR(40),
    state               VARCHAR(20),
    postalCode          VARCHAR(20),
    country             VARCHAR(40),
    userid              VARCHAR(20),
    password            VARCHAR(30),
    PRIMARY KEY (customerId)
);

CREATE TABLE paymentmethod (
    paymentMethodId     INT IDENTITY,
    paymentType         VARCHAR(20),
    paymentNumber       VARCHAR(30),
    paymentExpiryDate   DATE,
    customerId          INT,
    PRIMARY KEY (paymentMethodId),
    FOREIGN KEY (customerId) REFERENCES customer(customerid)
        ON UPDATE CASCADE ON DELETE CASCADE 
);

CREATE TABLE ordersummary (
    orderId             INT IDENTITY,
    orderDate           DATETIME,
    totalAmount         DECIMAL(10,2),
    shiptoAddress       VARCHAR(50),
    shiptoCity          VARCHAR(40),
    shiptoState         VARCHAR(20),
    shiptoPostalCode    VARCHAR(20),
    shiptoCountry       VARCHAR(40),
    customerId          INT,
    PRIMARY KEY (orderId),
    FOREIGN KEY (customerId) REFERENCES customer(customerid)
        ON UPDATE CASCADE ON DELETE CASCADE 
);

CREATE TABLE category (
    categoryId          INT IDENTITY,
    categoryName        VARCHAR(50),    
    PRIMARY KEY (categoryId)
);

CREATE TABLE product (
    productId           INT IDENTITY,
    productName         VARCHAR(40),
    productPrice        DECIMAL(10,2),
    productImageURL     VARCHAR(100),
    productImage        VARBINARY(MAX),
    productDesc         VARCHAR(1000),
    categoryId          INT,
    PRIMARY KEY (productId),
    FOREIGN KEY (categoryId) REFERENCES category(categoryId)
);

CREATE TABLE orderproduct (
    orderId             INT,
    productId           INT,
    quantity            INT,
    price               DECIMAL(10,2),  
    PRIMARY KEY (orderId, productId),
    FOREIGN KEY (orderId) REFERENCES ordersummary(orderId)
        ON UPDATE CASCADE ON DELETE NO ACTION,
    FOREIGN KEY (productId) REFERENCES product(productId)
        ON UPDATE CASCADE ON DELETE NO ACTION
);

CREATE TABLE incart (
    orderId             INT,
    productId           INT,
    quantity            INT,
    price               DECIMAL(10,2),  
    PRIMARY KEY (orderId, productId),
    FOREIGN KEY (orderId) REFERENCES ordersummary(orderId)
        ON UPDATE CASCADE ON DELETE NO ACTION,
    FOREIGN KEY (productId) REFERENCES product(productId)
        ON UPDATE CASCADE ON DELETE NO ACTION
);

CREATE TABLE warehouse (
    warehouseId         INT IDENTITY,
    warehouseName       VARCHAR(30),    
    PRIMARY KEY (warehouseId)
);

CREATE TABLE shipment (
    shipmentId          INT IDENTITY,
    shipmentDate        DATETIME,   
    shipmentDesc        VARCHAR(100),   
    warehouseId         INT, 
    PRIMARY KEY (shipmentId),
    FOREIGN KEY (warehouseId) REFERENCES warehouse(warehouseId)
        ON UPDATE CASCADE ON DELETE NO ACTION
);

CREATE TABLE productinventory ( 
    productId           INT,
    warehouseId         INT,
    quantity            INT,
    price               DECIMAL(10,2),  
    PRIMARY KEY (productId, warehouseId),   
    FOREIGN KEY (productId) REFERENCES product(productId)
        ON UPDATE CASCADE ON DELETE NO ACTION,
    FOREIGN KEY (warehouseId) REFERENCES warehouse(warehouseId)
        ON UPDATE CASCADE ON DELETE NO ACTION
);

CREATE TABLE review (
    reviewId            INT IDENTITY,
    reviewRating        INT,
    reviewDate          DATETIME,   
    customerId          INT,
    productId           INT,
    reviewComment       VARCHAR(1000),          
    PRIMARY KEY (reviewId),
    FOREIGN KEY (customerId) REFERENCES customer(customerId)
        ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (productId) REFERENCES product(productId)
        ON UPDATE CASCADE ON DELETE CASCADE
);

-- Ski Store sample data. Ski specs live in productDesc (the course schema is fixed).
INSERT INTO category(categoryName) VALUES ('All-Mountain');
INSERT INTO category(categoryName) VALUES ('Powder');
INSERT INTO category(categoryName) VALUES ('Carving');
INSERT INTO category(categoryName) VALUES ('Park/Freestyle');
INSERT INTO category(categoryName) VALUES ('Touring');
INSERT INTO category(categoryName) VALUES ('Kids');

INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Atomic Bent 100', 1, 'Lengths: 164/172/180/188 cm | Waist: 100 mm | Radius: 19 m | Profile: Rocker/Camber/Rocker. Playful freeride ski that floats in soft snow and still holds an edge on groomers.', 799.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Atomic Maverick 88 Ti', 1, 'Lengths: 161/169/176/184 cm | Waist: 88 mm | Radius: 16 m | Profile: Rocker/Camber. Titanal-reinforced daily driver for frontside cruising and mixed conditions.', 899.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Atomic Redster S9 Revoshock S', 3, 'Lengths: 155/160/165/170 cm | Waist: 68 mm | Radius: 13 m | Profile: Camber. World Cup inspired slalom carver for quick, precise turns on hard snow.', 1099.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Atomic Backland 98', 5, 'Lengths: 164/172/180/188 cm | Waist: 98 mm | Radius: 18 m | Profile: Rocker/Camber/Rocker. Lightweight touring ski with enough backbone for the descent.', 849.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Atomic Bent 110', 2, 'Lengths: 172/180/188 cm | Waist: 110 mm | Radius: 20 m | Profile: Rocker/Camber/Rocker. Wider Bent for deep days, with a surfy, loose feel in powder.', 899.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Atomic Bent Jr', 6, 'Lengths: 120/130/140/150 cm | Waist: 85 mm | Radius: 14 m | Profile: Rocker/Camber/Rocker. Junior version of the Bent for young skiers exploring the whole mountain.', 349.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Zag Harfang 96', 1, 'Lengths: 164/172/180/188 cm | Waist: 96 mm | Radius: 18 m | Profile: Rocker/Camber/Rocker. Versatile all-mountain ski from Chamonix, stable at speed and easy to pivot.', 899.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Zag Slap 112', 2, 'Lengths: 176/184/192 cm | Waist: 112 mm | Radius: 22 m | Profile: Rocker/Camber/Rocker. Big-mountain powder ski built for steep, deep Chamonix lines.', 949.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Zag Ubac 95', 5, 'Lengths: 163/171/179/187 cm | Waist: 95 mm | Radius: 19 m | Profile: Rocker/Camber. Light, damp touring ski for long approaches and variable snow.', 849.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Icelantic Nomad 105', 1, 'Lengths: 171/181/191 cm | Waist: 105 mm | Radius: 19 m | Profile: Rocker/Camber/Rocker. Wide all-mountain ski that charges through crud and loves soft snow.', 849.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Icelantic Saba Pro 117', 2, 'Lengths: 175/185/191 cm | Waist: 117 mm | Radius: 21 m | Profile: Rocker/Camber/Rocker. Hard-charging powder ski with a stiff, stable flex.', 949.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Icelantic Riveter 95', 4, 'Lengths: 161/171/181 cm | Waist: 95 mm | Radius: 18 m | Profile: Rocker/Camber/Rocker. Twin-tip freestyle ski that is just as happy in the park as on side hits.', 749.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Icelantic Mystic 97', 5, 'Lengths: 161/171/181 cm | Waist: 97 mm | Radius: 17 m | Profile: Rocker/Camber/Rocker. Featherweight touring ski for big days in the backcountry.', 899.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Armada ARV 96', 4, 'Lengths: 164/172/180/188 cm | Waist: 96 mm | Radius: 19 m | Profile: Rocker/Camber/Rocker. Durable twin-tip for park laps, jumps and all-mountain freestyle.', 699.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Armada ARV 88', 4, 'Lengths: 156/164/172/180 cm | Waist: 88 mm | Radius: 17 m | Profile: Rocker/Camber/Rocker. Narrower ARV for rails, boxes and buttery tricks.', 599.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Armada ARG II', 2, 'Lengths: 175/185/195 cm | Waist: 121 mm | Radius: 23 m | Profile: Rocker/Camber/Rocker. Iconic fat freestyle ski for slashing and spinning in deep snow.', 899.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Armada Declivity 92 Ti', 1, 'Lengths: 164/172/180/188 cm | Waist: 92 mm | Radius: 18 m | Profile: Rocker/Camber/Rocker. Metal-laminate all-mountain ski for confident, powerful turns.', 849.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Armada Bantam', 6, 'Lengths: 118/128/138/148 cm | Waist: 85 mm | Radius: 14 m | Profile: Rocker/Camber/Rocker. Junior twin-tip so young skiers can learn park and all-mountain tricks.', 299.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('K2 Mindbender 99Ti', 1, 'Lengths: 168/176/184/192 cm | Waist: 99 mm | Radius: 19 m | Profile: Rocker/Camber/Rocker. Torsion-control all-mountain ski for strong, aggressive skiers.', 949.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('K2 Disruption 82Ti', 3, 'Lengths: 163/170/177/184 cm | Waist: 82 mm | Radius: 15 m | Profile: Camber. Ice-gripping frontside carver for early-morning corduroy.', 899.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('K2 Wayback 98', 5, 'Lengths: 163/170/177/184 cm | Waist: 98 mm | Radius: 20 m | Profile: Rocker/Camber. Reliable touring ski with a light core and a forgiving flex.', 799.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('K2 Reckoner 102', 4, 'Lengths: 161/170/177/184 cm | Waist: 102 mm | Radius: 19 m | Profile: Rocker/Camber/Rocker. Soft, playful freestyle ski for the whole mountain.', 649.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('K2 Juvy', 6, 'Lengths: 99/109/119/129 cm | Waist: 75 mm | Radius: 11 m | Profile: Rocker/Camber/Rocker. Light, easy-turning kids ski with a twin tip for first tricks.', 249.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Blizzard Rustler 10', 1, 'Lengths: 164/172/180/188 cm | Waist: 102 mm | Radius: 19 m | Profile: Rocker/Camber/Rocker. Freeride ski that blends stability in crud with easy turn initiation.', 899.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Blizzard Sheeva 11', 2, 'Lengths: 164/172/180 cm | Waist: 112 mm | Radius: 18 m | Profile: Rocker/Camber/Rocker. Women''s powder ski that floats easily and stays nimble in trees.', 899.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Blizzard Firebird SRC', 3, 'Lengths: 155/160/165/170 cm | Waist: 68 mm | Radius: 13 m | Profile: Camber. Race-room slalom carver for short, snappy turns.', 999.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Blizzard Zero G 95', 5, 'Lengths: 164/171/178/185 cm | Waist: 95 mm | Radius: 20 m | Profile: Rocker/Camber. Carbon touring ski that climbs light and descends with confidence.', 899.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Blizzard Rustler Team', 6, 'Lengths: 128/138/148/158 cm | Waist: 88 mm | Radius: 15 m | Profile: Rocker/Camber/Rocker. Junior freeride ski for young rippers following the grown-up Rustlers.', 399.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Volkl Mantra 102', 1, 'Lengths: 170/177/184/191 cm | Waist: 102 mm | Radius: 21 m | Profile: Rocker/Camber. Powerful big-mountain ski that rewards a strong, technical skier.', 1049.99);
INSERT product(productName, categoryId, productDesc, productPrice) VALUES ('Volkl Deacon 76', 3, 'Lengths: 158/165/172/179 cm | Waist: 76 mm | Radius: 15 m | Profile: Camber. Precise on-piste carver with a smooth, damp ride.', 999.99);

INSERT INTO warehouse(warehouseName) VALUES ('Main warehouse');
INSERT INTO productInventory(productId, warehouseId, quantity, price) VALUES (1, 1, 5, 799.99);
INSERT INTO productInventory(productId, warehouseId, quantity, price) VALUES (2, 1, 10, 899.99);
INSERT INTO productInventory(productId, warehouseId, quantity, price) VALUES (3, 1, 3, 1099.99);
INSERT INTO productInventory(productId, warehouseId, quantity, price) VALUES (4, 1, 2, 849.99);
INSERT INTO productInventory(productId, warehouseId, quantity, price) VALUES (5, 1, 6, 899.99);
INSERT INTO productInventory(productId, warehouseId, quantity, price) VALUES (6, 1, 3, 349.99);
INSERT INTO productInventory(productId, warehouseId, quantity, price) VALUES (7, 1, 1, 899.99);
INSERT INTO productInventory(productId, warehouseId, quantity, price) VALUES (8, 1, 0, 949.99);
INSERT INTO productInventory(productId, warehouseId, quantity, price) VALUES (9, 1, 2, 849.99);
INSERT INTO productInventory(productId, warehouseId, quantity, price) VALUES (10, 1, 3, 849.99);

INSERT INTO customer (firstName, lastName, email, phonenum, address, city, state, postalCode, country, userid, password) VALUES ('Arnold', 'Anderson', 'a.anderson@gmail.com', '204-111-2222', '103 AnyWhere Street', 'Winnipeg', 'MB', 'R3X 45T', 'Canada', 'arnold' , '304Arnold!');
INSERT INTO customer (firstName, lastName, email, phonenum, address, city, state, postalCode, country, userid, password) VALUES ('Bobby', 'Brown', 'bobby.brown@hotmail.ca', '572-342-8911', '222 Bush Avenue', 'Boston', 'MA', '22222', 'United States', 'bobby' , '304Bobby!');
INSERT INTO customer (firstName, lastName, email, phonenum, address, city, state, postalCode, country, userid, password) VALUES ('Candace', 'Cole', 'cole@charity.org', '333-444-5555', '333 Central Crescent', 'Chicago', 'IL', '33333', 'United States', 'candace' , '304Candace!');
INSERT INTO customer (firstName, lastName, email, phonenum, address, city, state, postalCode, country, userid, password) VALUES ('Darren', 'Doe', 'oe@doe.com', '250-807-2222', '444 Dover Lane', 'Kelowna', 'BC', 'V1V 2X9', 'Canada', 'darren' , '304Darren!');
INSERT INTO customer (firstName, lastName, email, phonenum, address, city, state, postalCode, country, userid, password) VALUES ('Elizabeth', 'Elliott', 'engel@uiowa.edu', '555-666-7777', '555 Everwood Street', 'Iowa City', 'IA', '52241', 'United States', 'beth' , '304Beth!');

DECLARE @orderId int
-- Order 1 can be shipped as have enough inventory
INSERT INTO ordersummary (customerId, orderDate, totalAmount) VALUES (1, '2026-10-15 10:25:55', 3449.96)
SELECT @orderId = @@IDENTITY
INSERT INTO orderproduct (orderId, productId, quantity, price) VALUES (@orderId, 1, 1, 799.99)
INSERT INTO orderproduct (orderId, productId, quantity, price) VALUES (@orderId, 5, 2, 899.99)
INSERT INTO orderproduct (orderId, productId, quantity, price) VALUES (@orderId, 10, 1, 849.99);

INSERT INTO ordersummary (customerId, orderDate, totalAmount) VALUES (2, '2026-10-16 18:00:00', 4499.95)
SELECT @orderId = @@IDENTITY
INSERT INTO orderproduct (orderId, productId, quantity, price) VALUES (@orderId, 5, 5, 899.99);

-- Order 3 cannot be shipped as do not have enough inventory for item 7
INSERT INTO ordersummary (customerId, orderDate, totalAmount) VALUES (3, '2026-10-15 3:30:22', 3399.95)
SELECT @orderId = @@IDENTITY
INSERT INTO orderproduct (orderId, productId, quantity, price) VALUES (@orderId, 6, 2, 349.99)
INSERT INTO orderproduct (orderId, productId, quantity, price) VALUES (@orderId, 7, 3, 899.99);

INSERT INTO ordersummary (customerId, orderDate, totalAmount) VALUES (2, '2026-10-17 05:45:11', 14949.84)
SELECT @orderId = @@IDENTITY
INSERT INTO orderproduct (orderId, productId, quantity, price) VALUES (@orderId, 3, 4, 1099.99)
INSERT INTO orderproduct (orderId, productId, quantity, price) VALUES (@orderId, 8, 3, 949.99)
INSERT INTO orderproduct (orderId, productId, quantity, price) VALUES (@orderId, 13, 3, 899.99)
INSERT INTO orderproduct (orderId, productId, quantity, price) VALUES (@orderId, 28, 2, 399.99)
INSERT INTO orderproduct (orderId, productId, quantity, price) VALUES (@orderId, 29, 4, 1049.99);

INSERT INTO ordersummary (customerId, orderDate, totalAmount) VALUES (5, '2026-10-15 10:25:55', 8199.91)
SELECT @orderId = @@IDENTITY
INSERT INTO orderproduct (orderId, productId, quantity, price) VALUES (@orderId, 5, 4, 899.99)
INSERT INTO orderproduct (orderId, productId, quantity, price) VALUES (@orderId, 19, 2, 949.99)
INSERT INTO orderproduct (orderId, productId, quantity, price) VALUES (@orderId, 20, 3, 899.99);

-- Lab 8 images: every ski uses a grey placeholder for now (shop/img/placeholder.svg).
UPDATE Product SET productImageURL = 'img/placeholder.svg';
