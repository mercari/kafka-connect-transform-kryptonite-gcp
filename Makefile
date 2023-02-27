.PHONY: chk
chk:
	./mvnw spotless:check

.PHONY: fmt
fmt:
	./mvnw spotless:apply

.PHONY: test
test:
	./mvnw verify

.PHONY: jar
jar:
	./mvnw clean package
