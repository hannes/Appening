import sys,csv;
data = csv.reader(open(sys.argv[1]), delimiter='\t',quoting=csv.QUOTE_NONE)

print '<!DOCTYPE html>\n\
<html><head>\n\
<meta name="viewport" content="initial-scale=1.0, user-scalable=no" />\n\
    <style type="text/css">\n\
      html { height: 100% }\n\
      body { height: 100%; margin: 0; padding: 0 }\n\
      #map_canvas { height: 100% }\n\
    </style>\n\
    <script type="text/javascript" src="http://maps.googleapis.com/maps/api/js?key=AIzaSyCHCVlEq_VleQp6qKdOpPgGT1W1h3yG29s&sensor=false"> \n\
    </script>\n\
    <script type="text/javascript">\n\
      function initialize() {\n\
        var mapOptions = {\n\
          center: new google.maps.LatLng(52.3704,4.895),\n\
          zoom: 15,\n\
          mapTypeId: google.maps.MapTypeId.ROADMAP\n\
        };\n\
        var map = new google.maps.Map(document.getElementById("map_canvas"),\n\
            mapOptions);\n'


filteredData = []
i = 0;
for line in data:
	name = line[0];
	lat = float(line[1])
	lng = float(line[2])
	count = long(line[3])
	
	print 'var pos'+str(i)+'=new google.maps.LatLng('+str(lat)+','+str(lng)+');\n\
	var marker'+str(i)+'= new google.maps.Marker({\n\
		position:  pos'+str(i)+',\n\
		map: map,\n\
		title: \''+name.replace("'", "\\'") +' ('+str(count)+')\'\n\
	});\n'
	i = i+1
		
print '}</script></head><body onload="initialize()" style="background-color: green;"><div id="map_canvas"></div></body></html>'