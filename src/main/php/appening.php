<?php    
require_once("helper.inc.php");

date_default_timezone_set('UTC');
$startDate = strtotime("2012-09-21");
$endDate = time();
	
	
// single place pages
if (isset($_REQUEST['place'])) {
	$place = $_REQUEST['place'];
	if (!is_numeric($place)) {
		die("nice try!");
	}
	$name = mysql_escape_string(querySingle("SELECT `name` FROM `places` WHERE `id`='".$place."';"));
	if (empty($name)) {
		die("Unknown Place ".$place);
	}
	$created = "";
	if (isset($_REQUEST['date'])) {
		$date = strtotime($_REQUEST['date']);
		$created = " AND `created` >= '".date($datef,$date)."' AND `created` <= '".date($datef,$date+60*60*24)."' ";
	}

	$res = $mysqli->query("SELECT * FROM (SELECT `user`,`text`, MATCH (`text`) AGAINST ('".$name."' IN NATURAL LANGUAGE MODE) AS `score` FROM `messages` WHERE MATCH (`text`) AGAINST ('".$name."' IN NATURAL LANGUAGE MODE) ".$created.") AS `scores` WHERE `score`> ".$threshold.";");
	
	print "<html><body style=\"font-size: 8pt; font-family: sans-serif;\"><h1>".$name." [".$place."]</h1><table>";
	while ($row = $res->fetch_assoc()) {
		print "<tr><td>@<a href=\"http://twitter.com/".$row["user"]."\">".$row["user"]."</a></td><td>".$row["text"]."</td></tr>";
	}
	print "</table></body></html>";

}

// big overview page
else{
	
	$updated = querySingle("SELECT `updated` from `placeupdate` ORDER BY `updated` DESC LIMIT 1;");
	
	$res = $mysqli->query("
		SELECT `id`,`name`,`day`,SUM(`count`) AS `mentioned` FROM (SELECT  `place`, DATE(`start`) AS  `day`,`count` FROM  `counts` ) AS `ct` JOIN `places` ON `ct`.`place` = `places`.`id` GROUP BY `id`,`day`;");
	
	$mentions = array();
	while ($row = $res->fetch_assoc()) {
		$id = $row["id"];
		$mentions[$id]["mentions"][date($datef,strtotime($row["day"]))] = $row["mentioned"];
		if (!isset($mentions[$id]["name"])) {
			$mentions[$id]["name"] = $row["name"];
		}
	}
	
	// this is the time we analyze the whole thing for, can be set to saturday night for testing purposes
	//$ctime = "2012-09-24 13:00:00";
	$ctime = date("Y-m-d H:00:00");
	if (isset($_REQUEST["simdate"])) {
		$ctime = date("Y-m-d H:00:00",strtotime($_REQUEST["simdate"]));
	}
	$retInterval = 48;
	$minMentions = 15;
	
	// TODO: weights are guessed for now!
	$weight48 = 1;
	$weight24= 1;
	$weight12 = 2;
	$weight6 = 5;

	foreach ($mentions as $id => $mentionsD) {
		$name = $mentionsD["name"];
		if (array_sum($mentionsD["mentions"]) < $minMentions) {
			unset($mentions[$id]);
			continue;
		}
		
		$points = array();
		$res = $mysqli->query("SELECT UNIX_TIMESTAMP(`start`) AS `ts`,`count` FROM `counts` WHERE `place`=".$id." AND `start` > DATE_SUB('".$ctime."',INTERVAL ".$retInterval." HOUR) AND start < '".$ctime."' ORDER BY `start` ASC;");
			
		while ($row = $res->fetch_assoc()) {
			$y = $row["count"];
			$x = (strtotime($ctime) - $row["ts"]) / (60*60); 		
			$points[$x] = $y;
		}
		for ($i = 0; $i < $retInterval; $i++) {
			if (!isset($points[$i])) {
				$points[$i] = 0;
			}
		}
		krsort($points);
		
		$mentions[$id]["trendpoints"] = $points;
		$mentions[$id]["48trend"] = slope($points);
		$mentions[$id]["24trend"] = slope(lastHours($points,24));
		$mentions[$id]["12trend"] = slope(lastHours($points,12));
		$mentions[$id]["6trend"] = slope(lastHours($points,6));
		
		$mentions[$id]["trend"] = $weight48 * $mentions[$id]["48trend"] + 
			$weight24 * $mentions[$id]["24trend"] + 
			$weight12 * $mentions[$id]["12trend"] + 
			$weight6 * $mentions[$id]["6trend"];
	}
		
	function trendcompare($a, $b) {
		if ($a["trend"] > $b["trend"]) return -1;
		if ($a["trend"] < $b["trend"]) return 1;
		if ($a["trend"] == $b["trend"]) return 0;
	}
	uasort($mentions, "trendcompare");
		
	print "<html><head><title>Appening Amsterdam</title></head><body style=\"font-size: 8pt; font-family: sans-serif;\"><table>";
	print "<tr><td>Place</td>";
	
	$curDate = strtotime(date($datef,$startDate) . " 00:00:00");
	while ($curDate <= $endDate) {
		print "<td>".date("D",$curDate)."<br>".date("m-d",$curDate)."</td>";
		$curDate += 60*60*24;
	}
	
	function printTrend($a) {
		if ($a == 0) {
			return "<td style=\"color:grey; text-align:center;\">-</td>";
		}
		if ($a < 0) {
			return "<td style=\"color:red;\">".$a."</td>";
		} else {
			return "<td style=\"color:darkgreen;\">+".$a."</td>";
		}
	}
	
	print "<td>Plot</td><td>48h</td><td>24h</td><td>12h</td><td>6h</td><td>Weighted</td></tr>";
	foreach ($mentions as $id => $mentionsD) {
			$name = $mentionsD["name"];

			print "<tr><td><a href=\"?place=".$id."\">".$name."</a></td>";

		$curDate = $startDate;
		while ($curDate <= $endDate) {
			$col = "C8C8C8";
			if ($i % 2 == 0) $col = "99CCFF";
			$key = date("Y-m-d",$curDate);
			$cnt = "&nbsp;";
			if (isset($mentionsD["mentions"][$key])) {
				//$col = "red";
				$cnt = $mentionsD["mentions"][$key];
				$cnt = "<a href=\"?place=".$id."&amp;date=".$key."\">".$cnt."</a>";
			}
			print "<td style=\"width: 20px;color: white; text-align:center; vertical-align: middle;background-color:".$col.";\">".$cnt."</td>";
			$curDate += 60*60*24;
		}
		
		print "<td><img src=\"http://chart.googleapis.com/chart?cht=ls&chxr=0,0,72&chxs=0,676767,0,0,_,676767&chm=D,0033FF,0,0,1,1&chs=200x50&chds=a&chd=t:".implode(array_values($mentionsD["trendpoints"]),",")."&chxt=x,y\" /></td>";
		
		print printTrend($mentionsD["48trend"]);
		print printTrend($mentionsD["24trend"]);
		print printTrend($mentionsD["12trend"]);
		print printTrend($mentionsD["6trend"]);
		print printTrend($mentionsD["trend"]);
	}
		
	print "</table><br><br><small>&copy; Kathrin Dentler &amp; Hannes M&uuml;hleisen, 2012</small><br><small>Updated: ".$updated." UTC</small></body></html>";

}