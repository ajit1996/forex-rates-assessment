create extension hstore;
create schema forex;
create table forex."Rates"("from" VARCHAR NOT NULL, "to" VARCHAR NOT NULL,"bid" DECIMAL,"ask" DECIMAL,"price" DECIMAL,"time_stamp" VARCHAR NOT NULL, PRIMARY KEY("from", "to"));