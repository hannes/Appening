#!/bin/sh
TF=/tmp/appening.sql.gz
echo "loading dump from server"
wget -O $TF "http://appening.u0d.de/~ubuntu/appening.sql.gz"
echo "importing dump in local db"

echo "please give my your MySQL root pw:"
read mysqlpw
echo "please wait..."
mysql -u root -p"$mysqlpw" -e "CREATE DATABASE IF NOT EXISTS \`appening\`;"
gzcat $TF | mysql -u root -p"$mysqlpw" appening
echo "done!"
rm $TF
