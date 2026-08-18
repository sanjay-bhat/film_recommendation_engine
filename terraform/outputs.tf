output "container_name" {
  description = "Running container name"
  value       = docker_container.film_recommend.name
}

output "container_id" {
  description = "Running container ID"
  value       = docker_container.film_recommend.id
}

output "host_port" {
  description = "Port exposed on the host"
  value       = var.host_port
}

output "image_id" {
  description = "Built Docker image ID"
  value       = docker_image.film_recommend.image_id
}

output "network_name" {
  description = "Docker network name"
  value       = docker_network.film_recommend.name
}
