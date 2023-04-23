## Requirements

[Forex](forex-mtl) is a simple application that acts as a local proxy for getting exchange rates. It's a service that can be consumed by other internal services to get the exchange rate between a set of currencies, so they don't have to care about the specifics of third-party providers.

We provide you with an initial scaffold for the application with some dummy interpretations/implementations. For starters we would like you to try and understand the structure of the application, so you can use this as the base to address the following use case:

* The service returns an exchange rate when provided with 2 supported currencies 
* The rate should not be older than 5 minutes
* The service should support at least 10,000 successful requests per day with 1 API token

Please note the following drawback of the [One-Frame service](https://hub.docker.com/r/paidyinc/one-frame): 

> The One-Frame service supports a maximum of 1000 requests per day for any given authentication token. 

## Approach to address One-Frame service limitation
    * As mentioned above that service should not return rates older than 5 minutes , to overcome this limitation have added persitence layer (postgres db) to store live rates 
    * For each request , we're checking into db and if the rates are not older than 5 mins then getting those rates from db 
    * If the rates saved in db are older than 5 minutes then we're fetching the live rates from One-Frame Service and overwrite the live rates in db
    * Currency we've support exchange between 9 currencies so in total we can expect currency exchange requests for 36 different currency pairs.
    * If we save the rates in database and return the same rates if incoming request is within last 5 minute interval then we can serve minimum 288 * 36 = 10,368 request in one day (i.e As we have 288 times 5-minute interval in one day).

#### How to run locally

* Checkout the git repo (i.e ) and go to project directory
* Run following command in terminal to run one-frame-application and postgres in separate docker containers: **docker-compose up** 
* I've added Dockerfile for forex-rates app but for some reason even after adding network configuration for postgres in docker-compose getting issues running locally 
* As a workaround to run forex-rates service, Either we can run service from editor of any choice or from terminal and for ease have kept sbt package within repo. 

### Steps To Run Application :
    1 - Clone git repo (i.e git clone https://github.com/ajit1996/forex-rates-assessment.git )and go to project directory
    2 - Run command docker-compose up
    3 - Run command in separate terminal sbt/bin/sbt run forex.jar

### The Forex Rates API Sample Examples :

`GET /rates?from={from_currency}&to={to_currency}`
__API__
REQUEST  : `GET /rates?from=USD&to=USD`
RESPONSE : `{"message":"Same from_currency - USD and to_currency - USD can't be converted","timestamp":"2023-04-23T18:10:24.845921Z"}`

REQUEST  : `GET /rates?from=USD&to=JPY`
RESPONSE : `{"from":"USD","to":"JPY","price":0.246459103217167129,"timestamp":"2023-04-23T18:28:03.694084Z"}`

REQUEST  : `GET /rates?from=USD&to=ERR`
RESPONSE : `{"message":"Un-supported/Invalid to_currency","timestamp":"2023-04-23T18:28:30.077466Z"}`

REQUEST  : `GET /rates?from=ERR&to=JPY`
RESPONSE : `{"message":"Un-supported/Invalid from_currency","timestamp":"2023-04-23T18:29:14.509928Z"}`

Thank You!