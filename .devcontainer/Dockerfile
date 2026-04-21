FROM ubuntu:22.04
RUN apt update && apt upgrade -y && apt install -y \
    openssh-client \
    sudo \
    git \
    zip \
    unzip \
    python3.11 \
    python3.11-venv \
    python3-pip \
    # require graphviz to run PlantUML vscode plugin
    # more: https://plantuml.com/graphviz-dot
    graphviz \
    curl

# Let use some username as 'worker' instead of default root
RUN useradd -ms /bin/bash worker

#  Add new user worker to sudo group
RUN adduser worker sudo

# Ensure sudo group users are not 
# asked for a password when using 
# sudo command by ammending sudoers file
RUN echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers

# Switch to the worker user
USER worker

# Ensure ~/.local/bin is in PATH (needed for tools like GitHub Copilot CLI)
ENV PATH="/home/worker/.local/bin:$PATH"

# Install SDKMAN
RUN curl -s "https://get.sdkman.io" | bash
