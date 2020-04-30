clj_cmd = env clj -O:default-options

.PHONY: build
build:
	mkdir -p target
	$(clj_cmd) -A:depstar -m hf.depstar.uberjar target/ummoi.jar --suppress-clash -C -m ummoi.core

.PHONY: run
run:
	java -jar target/ummoi.jar $(ARGS)

.PHONY: deploy
deploy:
	mvn deploy:deploy-file -Dfile=target/ummoi.jar -DpomFile=pom.xml -DrepositoryId=clojars -Durl=https://clojars.org/repo/

.PHONY: test
test:
	$(clj_cmd) -A:test

.PHONY: autotest
autotest:
	$(clj_cmd) -A:test --watch
