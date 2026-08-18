terraform {
  required_version = ">= 1.5"

  required_providers {
    docker = {
      source  = "kreuzwerker/docker"
      version = "~> 3.0"
    }
  }
}

provider "docker" {}

# --- Docker Image ---

resource "docker_image" "film_recommend" {
  name = "${var.image_name}:${var.image_tag}"

  build {
    context    = var.project_root
    dockerfile = "Dockerfile"
    tag        = ["${var.image_name}:${var.image_tag}"]
  }

  triggers = {
    dockerfile  = filesha256("${var.project_root}/Dockerfile")
    recommend   = filesha256("${var.project_root}/src/recommend.py")
    requirements = filesha256("${var.project_root}/requirements.txt")
  }
}

# --- Docker Network ---

resource "docker_network" "film_recommend" {
  name   = "${var.project_name}-network"
  driver = "bridge"
}

# --- Docker Container ---

resource "docker_container" "film_recommend" {
  name  = var.project_name
  image = docker_image.film_recommend.image_id

  ports {
    internal = var.container_port
    external = var.host_port
  }

  networks_advanced {
    name = docker_network.film_recommend.name
  }

  restart = var.restart_policy

  env = [
    "MOVIE=${var.default_movie}",
    "PORT=${var.container_port}",
  ]

  healthcheck {
    test         = ["CMD-SHELL", "python -c 'import sys; sys.exit(0)'"]
    interval     = "30s"
    timeout      = "5s"
    retries      = 3
    start_period = "10s"
  }

  labels {
    label = "project"
    value = var.project_name
  }

  labels {
    label = "managed-by"
    value = "terraform"
  }
}
