FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY src/ ./src/
COPY lib/ ./lib/

RUN mkdir -p out && javac -encoding UTF-8 -cp "lib/*" -d out src/*.java

EXPOSE 8080

CMD ["sh", "-c", "java -cp 'out:lib/*' Main"]