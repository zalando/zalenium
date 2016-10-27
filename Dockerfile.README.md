## Build

    docker build -t zalenium .

## Run
Because Zalenium uses docker to scale on-demand we need to give it the `docker.sock` full access, this is known as "Docker alongside docker".

### With Sauce Labs
    export SAUCE_USERNAME="<yourUser>"
    export SAUCE_ACCESS_KEY="<yourSecret>"

    docker run --rm -ti --name grid -p 4444:4444 \
      -e SAUCE_USERNAME -e SAUCE_ACCESS_KEY \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v $(which docker):$(which docker) \
      zalenium start

### Without Sauce Labs
    docker run --rm -ti --name grid -p 4444:4444 \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v $(which docker):$(which docker) \
      zalenium start --sauceLabsEnabled false
