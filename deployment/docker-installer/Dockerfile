FROM ubuntu:26.04

RUN apt-get update && \
    apt-get install -y wget unzip gnupg python3 python3-pip curl && \
    wget -qO - https://www.mongodb.org/static/pgp/server-7.0.asc | gpg --dearmor -o /usr/share/keyrings/mongodb-server-7.0.gpg && \
    echo "deb [ arch=amd64,arm64 signed-by=/usr/share/keyrings/mongodb-server-7.0.gpg ] https://repo.mongodb.org/apt/ubuntu jammy/mongodb-org/7.0 multiverse" | tee /etc/apt/sources.list.d/mongodb-org-7.0.list && \
    apt-get update && \
    apt-get install -y mongodb-database-tools mongodb-mongosh && \
    pip3 install --break-system-packages gdown && \
    rm -rf /var/lib/apt/lists/*

RUN mongoimport --version && gdown --version

COPY install.sh /install.sh
RUN chmod +x /install.sh

ENTRYPOINT ["/install.sh"]
