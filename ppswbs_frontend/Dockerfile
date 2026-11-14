FROM node:20-alpine
WORKDIR /app

# Cache dependencies layer
COPY package*.json ./
RUN npm install

# Copy application files
COPY . .

EXPOSE 5173

CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173"]
