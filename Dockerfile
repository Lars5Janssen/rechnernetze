FROM ubuntu
ENV CI_PROJECT_NAME='svs23'
ENV CI_PROJECT_NAMESPACE='ful'
ENV DEBIAN_FRONTEND='noninteractive'
ENV TZ=Europe/Berlin
LABEL authors='Michael.Deichen@haw-hamburg.de'

RUN apt-get update && apt-get install -y \
        curl \
        gettext \
        less \
        libintl-perl \
        lynx \
        nano \
    	default-jre \
	    vim \
        net-tools \
        openssh-server \
	    git \
        tcpdump \
        telnet \
        tzdata \
        wget \
    && rm -rf /var/lib/apt/lists/*

# locale
RUN ln -fs /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
RUN dpkg-reconfigure --frontend noninteractive tzdata

# Setup ssh
RUN mkdir /var/run/sshd
# allow root login with key pair
RUN sed -i 's/#Port 22/Port 222/' /etc/ssh/sshd_config
# allow root login with key pair
RUN sed -i 's/#PermitRootLogin.*/PermitRootLogin\ prohibit-password/' /etc/ssh/sshd_config
# SSH login fix. Otherwise user is kicked off after login
RUN sed -i 's@session\s*required\s*pam_loginuid.so@session optional pam_loginuid.so@g' /etc/pam.d/sshd
ENV NOTVISIBLE='in users profile'
RUN echo 'export VISIBLE=now' >> /etc/profile

COPY ~/.ssh/authorized_keys /root/.ssh/authorized_keys
RUN chmod 0400 /root/.ssh/authorized_keys

# Add welcome message
RUN echo 'Welcome to our portal from '${CI_PROJECT_NAME}'.' > /etc/motd

EXPOSE 80

COPY entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh
CMD ["./entrypoint.sh"]
