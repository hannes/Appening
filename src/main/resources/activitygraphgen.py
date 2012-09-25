 
import sys,csv;
from datetime import datetime

data = csv.reader(open(sys.argv[1]), delimiter='\t',quoting=csv.QUOTE_NONE)

d = dict()
m = dict()
r = []

for line in data:
	id = int(line[0])
	name = line[1]
	rangestart = line[2]
	rangeend = line[3]
	mentioned = int(line[4])
	d[id] = name
	m.setdefault(id, []).append(mentioned)
	if len(d) < 2:
		r.append(rangestart)

print "<html><body style=\"font-size: 8pt; font-family: sans-serif;\"><table>"
print "<tr><td>Place</td>"

for rng in r:
	do = datetime.strptime(rng, '%Y-%m-%d')

	print "<td>"+do.strftime('%a')+"<br>"+do.strftime('%m-%d')+"</td>"

print "</tr>"
i = 0
for entry in d:
	if sum(m[entry]) > 2:
		i = i+1
		print "<tr><td>" +d[entry]+ "</td>"
		for mc in m[entry]:
			col = "C8C8C8"
			if i % 2 == 0: 
				col = "99CCFF"				
			cnt = "&nbsp;"
			if mc > 0:
				col = "red"
				cnt = str(mc)
			print "<td style=\"width: 20px;color: white; text-align:center; vertical-align: middle;background-color:"+col+";\">"+cnt+"</td>"
		print "</tr>"
print "</table></body></html>"