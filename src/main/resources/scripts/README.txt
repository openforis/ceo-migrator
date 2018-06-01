How to delete all
-------------------
groovy of-users-importer.groovy -t "jdbc:h2:~/dev/temp/of-suite/data/users;AUTO_SERVER=TRUE"

How to import users
-------------------
groovy of-users-importer.groovy -u "jdbc:h2:~/dev/temp/of-suite/data/users;AUTO_SERVER=TRUE" user-list.json

How to import groups
--------------------
groovy of-users-importer.groovy -g "jdbc:h2:~/dev/temp/of-suite/data/users;AUTO_SERVER=TRUE" institution-list.json "img/institution-logos"


How to import resources (Imagery)
---------------------------------
groovy of-users-importer.groovy -r "jdbc:h2:~/dev/temp/of-suite/data/users;AUTO_SERVER=TRUE" imagery-list.json