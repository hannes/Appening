DROP PROCEDURE IF EXISTS `oldMessagesMoveProc`;

DELIMITER //

CREATE DEFINER=CURRENT_USER PROCEDURE `oldMessagesMoveProc`()
    MODIFIES SQL DATA
    DETERMINISTIC
BEGIN

DECLARE `procedureName` VARCHAR(100) DEFAULT 'oldMessagesMoveProc';

DECLARE `cutoffHour` DATETIME;

SET `cutoffHour`= DATE_SUB(NOW(), INTERVAL 72 HOUR);

`procBody`:BEGIN
IF (EXISTS(SELECT `running` FROM `procstat` WHERE `procedure`=`procedureName` AND `running`=TRUE)) THEN
	LEAVE `procBody`;
END IF;
DELETE FROM `procstat` WHERE `procedure`=`procedureName`;
INSERT INTO `procstat` (`procedure`,`running`,`lastCompleted`) VALUES(`procedureName`,TRUE,NOW()); 

INSERT INTO `messagesold` SELECT * FROM `messages` WHERE `created` < `cutoffHour`;
DELETE FROM `messages` WHERE `created` < `cutoffHour`;

UPDATE `procstat` SET `running`=FALSE,`lastCompleted`=NOW() WHERE `procedure`=`procedureName`;

END;


END//

DELIMITER ;

-- scheduler config

SET GLOBAL event_scheduler = ON;
CREATE EVENT `oldMessagesMoveEvent` ON SCHEDULE EVERY 1 DAY DO CALL oldMessagesMoveProc();   

