import xml.etree.ElementTree as ET
tree = ET.parse("amsterdam.osm")
for e in tree.findall("(/osm/node|/osm/way)[tag[@k='name']]"):
    print e