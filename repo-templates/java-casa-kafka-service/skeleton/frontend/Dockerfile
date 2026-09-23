FROM node:22-alpine

ENV NODE_ENV=production
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force

COPY app.js ./
COPY src ./src
COPY views ./views
COPY locales ./locales

# Tag of this build, printed at startup. docker-compose and the image build
# script pass it in.
ARG RELEASE=local
ENV RELEASE=${RELEASE}

USER node
EXPOSE 3000
CMD ["node", "app.js"]
