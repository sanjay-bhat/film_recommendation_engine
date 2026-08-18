# Contributing

Thanks for your interest in contributing to the Film Recommendation Engine.

## Getting Started

1. Fork and clone the repository
2. Run `make setup` to install Python dependencies and download NLTK data
3. Run `make unzip` to extract the dataset CSVs
4. Run `make run MOVIE="Inception"` to verify everything works

## Making Changes

1. Create a feature branch from `main`
2. Make your changes
3. Run `make lint` to check Python style
4. Run `make build` to verify Go and Rust compile
5. Test with a few movie titles: `make run-all MOVIE="The Matrix"`
6. Commit with a clear message and open a PR against `main`

## What to Work On

- Bug fixes and edge case handling
- Performance improvements to the matching algorithm
- Additional language implementations
- Better keyword cleaning and synonym resolution
- UI improvements to the GitHub Pages site

## Code Style

| Language | Convention |
|:---------|:-----------|
| Python | PEP 8, snake_case, f-strings |
| Go | gofmt, camelCase |
| Rust | rustfmt, snake_case |
| C# | .NET conventions, PascalCase |

## Reporting Issues

Use the [issue templates](https://github.com/sanjay-bhat/film_recommendation_engine/issues/new/choose) for bug reports and feature requests.

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
