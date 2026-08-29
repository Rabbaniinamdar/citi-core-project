CREATE USER IF NOT EXISTS 'replicator'@'%'
    IDENTIFIED WITH mysql_native_password BY 'replicapass';
GRANT REPLICATION SLAVE ON *.* TO 'replicator'@'%';
GRANT REPLICATION CLIENT ON *.* TO 'replicator'@'%';

CREATE USER IF NOT EXISTS 'citicore_readonly'@'%'
    IDENTIFIED WITH mysql_native_password BY 'replica123';
GRANT SELECT ON citicore_account.* TO 'citicore_readonly'@'%';

FLUSH PRIVILEGES;