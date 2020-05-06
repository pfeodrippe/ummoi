# Um Mói

## Examples

Check `examples` directory.

### Python 3 web server

Install flask and run `um`.

``` shell
$ pip3 install flask
$ FLASK_APP=examples/example_using_rest.py python3 -m flask run
# in another terminal
$ cd examples && um # or `um -c examples/ummoi.edn`
```

### Python 3 script

You can use `json` files instead of `edn`.

``` shell
$ um -c examples/py_env.json
```

### Babashka web server

Check https://github.com/borkdude/babashka/ and download babashka.

``` shell
$ bb examples/example_bb.clj
# in another terminal
$ um -c examples/bb.edn
```
