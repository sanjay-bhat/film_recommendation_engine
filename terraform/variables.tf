variable "project_name" {
  description = "Project name used for resource naming"
  type        = string
  default     = "film-recommend"
}

variable "project_root" {
  description = "Path to the project root directory"
  type        = string
  default     = ".."
}

variable "image_name" {
  description = "Docker image name"
  type        = string
  default     = "film-recommend"
}

variable "image_tag" {
  description = "Docker image tag"
  type        = string
  default     = "latest"
}

variable "container_port" {
  description = "Port the container listens on"
  type        = number
  default     = 8080
}

variable "host_port" {
  description = "Port exposed on the host"
  type        = number
  default     = 8080
}

variable "default_movie" {
  description = "Default movie for recommendations"
  type        = string
  default     = "The Dark Knight Rises"
}

variable "restart_policy" {
  description = "Container restart policy"
  type        = string
  default     = "unless-stopped"
}
