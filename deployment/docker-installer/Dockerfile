# Dockerfile für ein Image mit mongotools (inkl. mongoinstall), unzip und wget
FROM ubuntu:24.04

# Installiere benötigte Tools und MongoDB Database Tools
RUN apt-get update && \
    apt-get install -y wget unzip gnupg && \
    wget -qO - https://www.mongodb.org/static/pgp/server-7.0.asc | apt-key add - && \
    echo "deb [ arch=amd64,arm64 ] https://repo.mongodb.org/apt/ubuntu focal/mongodb-org/7.0 multiverse" | tee /etc/apt/sources.list.d/mongodb-org-7.0.list && \
    apt-get update && \
    apt-get install -y mongodb-database-tools && \
    rm -rf /var/lib/apt/lists/*

# mongoinstall ist Teil der mongodb-database-tools
# Überprüfe Installation
RUN mongodump --version && unzip -v && wget --version

COPY install.sh /install.sh

# Standardbefehl
CMD ["/install.sh"]
