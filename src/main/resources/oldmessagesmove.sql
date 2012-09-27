DROP PROCEDURE IF EXISTS `oldMessagesMoveProc`;

DELIMITER //

CREATE DEFINER=CURRENT_USER PROCEDURE `oldMessagesMoveProc`()
    MODIFIES SQL DATA
    DETERMINISTIC
BEGIN

DECLARE `cutoffHour` DATETIME;
SET `cutoffHour`= DATE_SUB(NOW(), INTERVAL 72 HOUR);

INSERT INTO `messagesold` SELECT * FROM `messages` WHERE `created` < `cutoffHour`;
DELETE FROM `messages` WHERE `created` < `cutoffHour`;

END//

DELIMITER ;

-- scheduler config

SET GLOBAL event_scheduler = ON;
CREATE EVENT `oldMessagesMoveEvent` ON SCHEDULE EVERY 1 DAY DO CALL oldMessagesMoveProc();   

