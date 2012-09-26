DROP PROCEDURE IF EXISTS generateRealtimeProc;

DELIMITER //

CREATE DEFINER=CURRENT_USER PROCEDURE `generateRealtimeProc`()
    MODIFIES SQL DATA
    DETERMINISTIC
BEGIN
DECLARE `threshold` INT DEFAULT 13;
DECLARE `presentCount` INT;
DECLARE `startHour`,`endHour` DATETIME;

DECLARE `done` INT DEFAULT FALSE;
DECLARE `pid` INT DEFAULT 0;
DECLARE `pname` VARCHAR(200);

DECLARE `placesCursor` CURSOR FOR SELECT `id`,`name` FROM `places`;
DECLARE CONTINUE HANDLER FOR NOT FOUND SET `done` = TRUE;

SET time_zone='+0:00';


SET `startHour`= DATE_SUB(DATE_SUB(NOW(), INTERVAL SECOND(NOW()) SECOND) , INTERVAL MINUTE(NOW()) MINUTE);
SET `endHour`= NOW();

OPEN `placesCursor`;

`placesLoop`: LOOP
	FETCH `placesCursor` INTO `pid`,`pname`;
	IF `done` THEN
		LEAVE `placesLoop`;
	END IF;
	DELETE FROM `nowcounts` WHERE `place`=`pid`;
	SET `presentCount` = 
		fulltext_score(`pname`,`threshold`,`startHour`,`endHour`);   	
	IF `presentCount` > 0 THEN 
		INSERT INTO `nowcounts` (`place`,`count`) VALUES (`pid`,`presentCount`);		
	END IF;			
		
END LOOP;
CLOSE `placesCursor`;
	
END//

DELIMITER ;

-- scheduler config

SET GLOBAL event_scheduler = ON;
CREATE EVENT `generateRealtimeEvent` ON SCHEDULE EVERY 5 MINUTE DO CALL generateRealtimeProc();     