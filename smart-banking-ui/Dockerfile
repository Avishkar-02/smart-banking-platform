# Stage 1: Build the React application
FROM node:18-alpine AS builder
WORKDIR /app

# Copy dependency files first to leverage Docker caching layers
COPY package*.json ./
RUN npm install

# Copy the rest of the application source code
COPY . .

# Build the production-ready static assets into the /app/build directory
RUN npm run build

# Stage 2: Serve static files with Nginx
FROM nginx:alpine

# Copy the build output over to Nginx's default public directory
COPY --from=builder /app/build /usr/share/nginx/html

# Copy a custom Nginx configuration to support client-side routing (React Router)
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]