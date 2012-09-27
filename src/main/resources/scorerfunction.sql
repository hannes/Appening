DELIMITER //


DROP FUNCTION IF EXISTS `fulltext_score`//

CREATE DEFINER=current_user() FUNCTION `fulltext_score`(`paramPlace` VARCHAR(100) CHARSET utf8, `paramThreshold` FLOAT, `startDate` DATETIME, `endDate` DATETIME) RETURNS int(11)
    DETERMINISTIC
BEGIN

DECLARE `ct` INT;

SELECT COUNT(`id`) INTO `ct` FROM `messages` WHERE MATCH (`text`) AGAINST (`paramPlace` IN NATURAL LANGUAGE MODE) > `paramThreshold` AND `created` > `startDate` AND `created` < `endDate`;

RETURN (`ct`);

END//

DELIMITER ;



