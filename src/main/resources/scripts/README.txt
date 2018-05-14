How to import users
-------------------
groovy of-users-importer.groovy -u user-list.json "jdbc:h2:~/dev/temp/of-suite/data/users;AUTO_SERVER=TRUE"


How to import groups
--------------------
groovy of-users-importer.groovy -g institution-list.json "jdbc:h2:~/dev/temp/of-suite/data/users;AUTO_SERVER=TRUE" "img/institution-logos"


How to import resources (Imagery)
---------------------------------
groovy of-users-importer.groovy -r imagery-list.json "jdbc:h2:~/dev/temp/of-suite/data/users;AUTO_SERVER=TRUE"