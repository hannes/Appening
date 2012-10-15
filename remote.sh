#!/bin/sh
TF=/tmp/appening.sql.gz
stty -echo
read -p "MySQL Root PW: " mysqlpw; echo
stty echo

echo "loading dump from server"
curl -o $TF "http://appening.u0d.de/~ubuntu/appening.sql.gz"
echo "importing dump in local db..."
mysql -u root -p"$mysqlpw" -e "CREATE DATABASE IF NOT EXISTS \`appening\`;"
gzcat $TF | mysql -u root -p"$mysqlpw" appening
echo "done!"
rm $TF
