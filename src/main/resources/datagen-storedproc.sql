DROP PROCEDURE IF EXISTS generateCountsProc;

DELIMITER //

CREATE DEFINER=CURRENT_USER PROCEDURE `generateCountsProc`( `generateDate` DATE)
    MODIFIES SQL DATA
    DETERMINISTIC
BEGIN

DECLARE `procedureName` VARCHAR(100) DEFAULT 'generateCountsProc';

DECLARE `threshold` INT DEFAULT 13;
DECLARE `presentCount` INT;
DECLARE `minHour`,`maxHour`,`curHour`,`endHour` DATETIME;

DECLARE `done` INT DEFAULT FALSE;
DECLARE `pid` INT DEFAULT 0;
DECLARE `pname` VARCHAR(200);

DECLARE `placesCursor` CURSOR FOR SELECT `id`,`name` FROM `places`;
DECLARE CONTINUE HANDLER FOR NOT FOUND SET `done` = TRUE;

SET time_zone='+0:00';

SET `minHour` = DATE_ADD(`generateDate`, INTERVAL 0 SECOND);
SET `maxHour`= DATE_SUB(DATE_SUB(NOW(), INTERVAL SECOND(NOW()) SECOND) , INTERVAL MINUTE(NOW()) MINUTE);

`procBody`:BEGIN
IF (EXISTS(SELECT `running` FROM `procstat` WHERE `procedure`=`procedureName` AND `running`=TRUE)) THEN
	LEAVE `procBody`;
END IF;
DELETE FROM `procstat` WHERE `procedure`=`procedureName`;
INSERT INTO `procstat` (`procedure`,`running`,`lastCompleted`) VALUES(`procedureName`,TRUE,NOW()); 


OPEN `placesCursor`;
`placesLoop`: LOOP
	FETCH `placesCursor` INTO `pid`,`pname`;
	IF `done` THEN
		LEAVE `placesLoop`;
	END IF;
	
	IF EXISTS(SELECT `updated` FROM `placeupdate` WHERE `place`=`pid`) THEN
		SELECT `updated` INTO `curHour` FROM `placeupdate` WHERE `place`=`pid`;
		DELETE FROM `placeupdate` WHERE `place`=`pid`;
	ELSE
		SET `curHour`= `minHour`;
	END IF;
	
	WHILE `curHour` < `maxHour` DO
		SET `endHour` = DATE_ADD(`curHour`, INTERVAL 1 HOUR);
	 	SET `presentCount` = 
	 		fulltext_score(`pname`,`threshold`,`curHour`,`endHour`);   	
		IF `presentCount` > 0 THEN 
			INSERT INTO `counts` (`place`,`start`,`end`,`count`) VALUES 
				(`pid`,`curHour`,`endHour`,`presentCount`);	
		END IF;
		SET `curHour` = `endHour`;	
	END WHILE;
	
	INSERT INTO `placeupdate` (`place`,`updated`) VALUES (`pid`,`maxHour`);
	
END LOOP;
CLOSE `placesCursor`;

UPDATE `procstat` SET `running`=FALSE,`lastCompleted`=NOW() WHERE `procedure`=`procedureName`;

END;
	
END//

DELIMITER ;

-- scheduler config

SET GLOBAL event_scheduler = ON;
CREATE EVENT `generateCountsEvent` ON SCHEDULE EVERY 1 HOUR DO CALL generateCountsProc('2012-09-21');      
	