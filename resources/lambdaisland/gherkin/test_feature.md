# Is it Friday yet?

Everybody wants to know when it's Friday

## Background: 

- <em>Given </em> a gregorian calendar

- <em>Given </em> the following users exist:

| name | email | twitter |
|---|---|---|
| Aslak | aslak@cucumber.io | @aslak_hellesoy |
| Julien | julien@cucumber.io | @jbpros |
| Matt | matt@cucumber.io | @mattwynne |


- <em>Given </em> a file "hello.txt" with

``` markdown
Dear bozo,

Please click this link to reset your password
```



## Sunday isn't Friday

- <em>Given </em> today is Sunday

- <em>When </em> I ask whether it's Friday yet

- <em>Then </em> I should be told "Nope"


## Friday is Friday

- <em>Given </em> today is Friday

- <em>When </em> I ask whether it's Friday yet

- <em>Then </em> I should be told "Indeed"


