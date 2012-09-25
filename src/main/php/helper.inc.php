<?php
$host = "localhost"; 
$user = "root";
$pass = "23112484";
$db = "appening";  

$threshold = 13;


$mysqli = new mysqli($host, $user, $pass, $db);
if ($mysqli->connect_errno) {
  die("Failed to connect to MySQL: (" . $mysqli->connect_errno . ") " .  $mysqli->connect_error);
}
date_default_timezone_set("Europe/Amsterdam");
$datef = "Y-m-d";

$file = "/tmp/appening-php-cache";

function querySingle($sql) {
	global $mysqli;
	$res = $mysqli->query($sql);
	if ($res->num_rows > 0) {
		$arrRes = $res->fetch_array();
		return $arrRes[0];
	}
}

function slope($points) {	
	if (sizeof($points) < 2) {
		return 0;
	}
	$x = array_keys($points);
	$y = array_values($points);
	
	// fitted curve: y=a+b*x
	// a= (sum(y)*sum(x^2) - sum(x)*sum(x*y))  / (n*sum(x^2)-(sum(x))^2)
	// b = (n*sum(x*y) - sum(x)*sum(y)) / (n*sum(x^2)-(sum(x))^2)
	
	// http://www.efunda.com/math/leastsquares/lstsqr1dcurve.cfm
	
	$sx2 = array_sum(array_map(create_function('$x', 'return pow($x,2);'), $x));
	$sx = array_sum($x);
	$sy = array_sum($y);
	$sxy = array_sum(array_map(create_function('$x,$y', 'return $x*$y;'), $x, $y));
	$n = count($x);
	
	$denom = $n*$sx2-pow($sx,2);
	//$a = ($sy*sx2 - $sx*$sxy) / $denom; // not interesting?
	$b = ($n*$sxy - $sx*$sy) / $denom;
	return round($b,2);
}


function lastHours($points,$hours) {
	$rx = array();
	foreach ($points as $x => $y) {
		if ($x < $hours) {
			$rx[$x] = $y;
		}
	}
	return $rx;
}