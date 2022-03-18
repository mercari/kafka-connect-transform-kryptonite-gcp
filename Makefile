.PHONY: chk
chk:
	 mvn com.coveo:fmt-maven-plugin:check

.PHONY: fmt
fmt:
	 mvn com.coveo:fmt-maven-plugin:format

.PHONY: test
test:
	mvn verify

.PHONY: jar
jar:
	mvn clean package
