MOVIE ?= The Dark Knight Rises
DATA_DIR ?= dataset

.PHONY: all build run clean setup unzip

all: build

# --- Setup ---

setup: unzip
	pip install -r requirements.txt
	python -c "import nltk; nltk.download('wordnet', quiet=True)"

unzip:
	@cd $(DATA_DIR) && \
	for z in *.zip; do \
		csv=$$(basename "$$z" .zip); \
		[ -f "$$csv" ] || unzip -o "$$z"; \
	done

# --- Build ---

build: build-go build-rust build-csharp

build-go:
	go build -o bin/recommend-go src/recommend.go

build-rust:
	rustc src/recommend.rs -o bin/recommend-rs --edition 2021

build-csharp:
	dotnet new console -o bin/csharp-build --no-restore 2>/dev/null || true
	cp src/Recommend.cs bin/csharp-build/Program.cs
	cd bin/csharp-build && dotnet build --nologo -q

# --- Run ---

run: run-python

run-python: unzip
	python src/recommend.py --movie "$(MOVIE)" --data-dir $(DATA_DIR)

run-go: build-go unzip
	./bin/recommend-go --movie "$(MOVIE)" --data-dir $(DATA_DIR)

run-rust: build-rust unzip
	./bin/recommend-rs --movie "$(MOVIE)" --data-dir $(DATA_DIR)

run-all: unzip build
	@echo "=== Python ===" && python src/recommend.py --movie "$(MOVIE)" --data-dir $(DATA_DIR)
	@echo "\n=== Go ===" && ./bin/recommend-go --movie "$(MOVIE)" --data-dir $(DATA_DIR)
	@echo "\n=== Rust ===" && ./bin/recommend-rs --movie "$(MOVIE)" --data-dir $(DATA_DIR)

# --- Docker ---

docker-build:
	docker build -t film-recommend .

docker-run:
	docker run --rm film-recommend --movie "$(MOVIE)"

# --- Lint ---

lint:
	flake8 src/recommend.py --max-line-length=120 --ignore=E501,W503

# --- Clean ---

clean:
	rm -rf bin/
	rm -f $(DATA_DIR)/*.csv
