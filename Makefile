.PHONY: chk
chk:
	mvn spotless:check

.PHONY: fmt
fmt:
	mvn spotless:apply

.PHONY: test
test:
	mvn verify

.PHONY: jar
jar:
	mvn clean package
