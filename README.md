# Smartcloud instance prices API

## How to run
### Prerequisites
* Java (>=8)
* sbt
### Run
* Run the server. Http port can be changed by [config](src/main/resources/application.conf)
```bash
sbt run
```
The API should be running on your port 8080 for default.

## API endpoints
### Instance kinds
* **URL**: GET /instance-kinds
* **Success Response**:
```aidl
[
    {
        "kind": "sc2-micro"
    },
    {
        "kind": "sc2-small"
    },
... (ommitted)
    {
        "kind": "sc2-hicpu-32"
    }
]
```
### Prices
Return prices of all instances. If `kind` is specified, the prices of the given instance kinds are returned.
* **URL**: GET /prices
* **Parameters**:
  * Optional:
    * kind=[string] : The value is one or multiple instance kinds. For multiple instance kinds, you can specify multiple instance kinds by comma separation. 
    For example, `/prices?kind=sc2-hicpu-32,sc2-small,sc2-micro`
* **Success Response**:
```aidl
[
    {
        "kind": "sc2-micro",
        "price": 0.301
    },
    {
        "kind": "sc2-small",
        "price": 0.561
    },
... (ommited)
    {
        "kind": "sc2-hicpu-32",
        "price": 0.925
    }
]
```

## Assumption and design
### Assumption
* `/prices` should return multiple prices. It is not clear by the requirements but returning one prices is subset of multiple one so I hope this is fine.
* 500 error of the smartcloud can be fixed in next 3 times calling the api.

### Design
* One of smarcloud api calls returns 422 error, this api returns 422 error instead of returning prices partially. I choose this way because this way is simpler for callee of this API. 
* Set retry policy for http client without waiting 
* Use `ApplicativeError` instead `Either`. This is because of keeping original type. 
* Set `errorHandle` in the `routes` instead of `getPrices` and `getInstanceKinds` because we may forget to set `errorHandle` in the new route method if we add a new endpoint. 
* I did `case ex : PriceService.Exception` in the `errorHandle` because we can get exhaustive type checking benefit by `sealed trait`.
