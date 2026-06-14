db = db.getSiblingDB('event_gateway_db');
db.createUser({
  user: 'gateway_user',
  pwd: process.env.GATEWAY_DB_PASSWORD || 'changeme',
  roles: [{ role: 'readWrite', db: 'event_gateway_db' }]
});
db.events.createIndex({ eventId: 1 }, { unique: true });
db.events.createIndex({ accountId: 1, eventTimestamp: 1 });
db.events.createIndex({ status: 1 });
db.events.createIndex({ createdAt: 1 });

db = db.getSiblingDB('account_service_db');
db.createUser({
  user: 'account_svc_user',
  pwd: process.env.ACCOUNT_SVC_DB_PASSWORD || 'changeme',
  roles: [{ role: 'readWrite', db: 'account_service_db' }]
});
db.accounts.createIndex({ accountId: 1 }, { unique: true });
db.transactions.createIndex({ eventId: 1 }, { unique: true });
db.transactions.createIndex({ accountId: 1, eventTimestamp: 1 });
