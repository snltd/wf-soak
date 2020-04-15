# wf-soaker

Send a predeterminted number of random points to a Wavefront proxy for a
predetermined time.

Useful for soak-testing and rightsizing proxies.

Runs as a container.

## Build

```
$ docker build -t wf-soaker .
```

## Run

```
$ docker run -it -e WF_PROXY="http://wavefront.localnet:2878" wf-soaker
```

## Settings

You can set the following through environment variables.

* `WF_PROXY`: where the proxy is. Requires the protocol and port
number: `http://wavefront.localnet:2878`. No default.
* `WF_PATH`: the base metric path [default `dev.soak`]
* `WF_INTERVAL`: send a bundle of metrics every this-many seconds [default 1]
* `WF_PARALLEL_METRICS`: each interval send a bundle of this-many metrics
  [default 10].
* `WF_PARALLEL_TAGS`: each of those metrics repeats this many times with
  this many different values for the 'dtag' point tag.
* `WF_THREADS`: duplicate the metric bundle this many times [default 10].
* `WF_ITERATIONS`: send this many bundles of metrics.
* `WF_DEBUG`: print debug info to standard out.

So the number of points-per-second we send is
`(WF_PARALLEL_METRICS × WF_PARALLEL_TAGS × WF_THREADS) ÷ WF_INTERVAL`
