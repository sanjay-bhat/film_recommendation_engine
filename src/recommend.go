// Film Recommendation Engine — Content-based movie recommender using TMDb 5000.
//
// Usage:
//
//	go run recommend.go --movie "The Dark Knight Rises" --data-dir ../data
//	go run recommend.go --id 12 --data-dir ../data
package main

import (
	"encoding/csv"
	"encoding/json"
	"flag"
	"fmt"
	"math"
	"os"
	"strconv"
	"strings"
)

type Film struct {
	Title        string
	Genres       string
	PlotKeywords string
	Director     string
	Actor1       string
	Actor2       string
	Actor3       string
	Year         int
	VoteAverage  float64
	VoteCount    int
	Popularity   float64
}

type NamedItem struct {
	Name string `json:"name"`
}

type CrewMember struct {
	Name string `json:"name"`
	Job  string `json:"job"`
}

type CastMember struct {
	Name string `json:"name"`
}

type Candidate struct {
	Title     string
	Year      int
	Score     float64
	Votes     int
	Index     int
	RankScore float64
}

func loadMovies(path string) ([]map[string]interface{}, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	reader := csv.NewReader(f)
	headers, err := reader.Read()
	if err != nil {
		return nil, err
	}

	var rows []map[string]interface{}
	for {
		record, err := reader.Read()
		if err != nil {
			break
		}
		row := make(map[string]interface{})
		for i, h := range headers {
			if i < len(record) {
				row[h] = record[i]
			}
		}
		rows = append(rows, row)
	}
	return rows, nil
}

func pipeNames(jsonStr string) string {
	var items []NamedItem
	if err := json.Unmarshal([]byte(jsonStr), &items); err != nil {
		return ""
	}
	names := make([]string, 0, len(items))
	for _, item := range items {
		names = append(names, item.Name)
	}
	return strings.Join(names, "|")
}

func getDirector(jsonStr string) string {
	var crew []CrewMember
	if err := json.Unmarshal([]byte(jsonStr), &crew); err != nil {
		return ""
	}
	for _, m := range crew {
		if m.Job == "Director" {
			return m.Name
		}
	}
	return ""
}

func getCastMember(jsonStr string, index int) string {
	var cast []CastMember
	if err := json.Unmarshal([]byte(jsonStr), &cast); err != nil {
		return ""
	}
	if index < len(cast) {
		return cast[index].Name
	}
	return ""
}

func buildFilms(movies, credits []map[string]interface{}) []Film {
	films := make([]Film, len(movies))
	for i, m := range movies {
		year := 0
		if rd, ok := m["release_date"].(string); ok && len(rd) >= 4 {
			year, _ = strconv.Atoi(rd[:4])
		}
		va, _ := strconv.ParseFloat(fmt.Sprintf("%v", m["vote_average"]), 64)
		vc, _ := strconv.Atoi(fmt.Sprintf("%v", m["vote_count"]))
		pop, _ := strconv.ParseFloat(fmt.Sprintf("%v", m["popularity"]), 64)

		var c map[string]interface{}
		if i < len(credits) {
			c = credits[i]
		}

		film := Film{
			Title:        fmt.Sprintf("%v", m["title"]),
			Genres:       pipeNames(fmt.Sprintf("%v", m["genres"])),
			PlotKeywords: pipeNames(fmt.Sprintf("%v", m["keywords"])),
			Year:         year,
			VoteAverage:  va,
			VoteCount:    vc,
			Popularity:   pop,
		}
		if c != nil {
			crewStr := fmt.Sprintf("%v", c["crew"])
			castStr := fmt.Sprintf("%v", c["cast"])
			film.Director = getDirector(crewStr)
			film.Actor1 = getCastMember(castStr, 0)
			film.Actor2 = getCastMember(castStr, 1)
			film.Actor3 = getCastMember(castStr, 2)
		}
		films[i] = film
	}
	return films
}

func getFeatures(film Film) []string {
	var features []string
	if film.Director != "" {
		features = append(features, film.Director)
	}
	for _, a := range []string{film.Actor1, film.Actor2, film.Actor3} {
		if a != "" {
			features = append(features, a)
		}
	}
	if film.PlotKeywords != "" {
		for _, kw := range strings.Split(film.PlotKeywords, "|") {
			if kw != "" {
				features = append(features, kw)
			}
		}
	}
	if film.Genres != "" {
		for _, g := range strings.Split(film.Genres, "|") {
			if g != "" {
				features = append(features, g)
			}
		}
	}
	return features
}

func euclidean(a, b []int) float64 {
	sum := 0
	for i := range a {
		d := a[i] - b[i]
		sum += d * d
	}
	return math.Sqrt(float64(sum))
}

func findNeighbors(films []Film, targetIdx, n int) []int {
	targetFeatures := getFeatures(films[targetIdx])
	allGenres := make(map[string]bool)
	for _, f := range films {
		if f.Genres != "" {
			for _, g := range strings.Split(f.Genres, "|") {
				allGenres[g] = true
			}
		}
	}
	featureSet := make(map[string]bool)
	for _, f := range targetFeatures {
		featureSet[f] = true
	}
	for g := range allGenres {
		featureSet[g] = true
	}
	featureList := make([]string, 0, len(featureSet))
	for f := range featureSet {
		featureList = append(featureList, f)
	}

	vectors := make([][]int, len(films))
	for i, film := range films {
		filmFeats := make(map[string]bool)
		for _, f := range getFeatures(film) {
			filmFeats[f] = true
		}
		vec := make([]int, len(featureList))
		for j, f := range featureList {
			if filmFeats[f] {
				vec[j] = 1
			}
		}
		vectors[i] = vec
	}

	type distPair struct {
		idx  int
		dist float64
	}
	dists := make([]distPair, len(films))
	for i := range films {
		dists[i] = distPair{i, euclidean(vectors[targetIdx], vectors[i])}
	}

	for i := 0; i < len(dists); i++ {
		for j := i + 1; j < len(dists); j++ {
			if dists[j].dist < dists[i].dist {
				dists[i], dists[j] = dists[j], dists[i]
			}
		}
	}

	result := make([]int, 0, n)
	for i := 0; i < n && i < len(dists); i++ {
		result = append(result, dists[i].idx)
	}
	return result
}

func gaussian(x, y, sigma float64) float64 {
	if sigma == 0 {
		return 0
	}
	return math.Exp(-math.Pow(x-y, 2) / (2 * sigma * sigma))
}

func levenshtein(a, b string) int {
	la, lb := len(a), len(b)
	if la == 0 {
		return lb
	}
	if lb == 0 {
		return la
	}
	matrix := make([][]int, la+1)
	for i := range matrix {
		matrix[i] = make([]int, lb+1)
		matrix[i][0] = i
	}
	for j := 0; j <= lb; j++ {
		matrix[0][j] = j
	}
	for i := 1; i <= la; i++ {
		for j := 1; j <= lb; j++ {
			cost := 1
			if a[i-1] == b[j-1] {
				cost = 0
			}
			matrix[i][j] = min3(matrix[i-1][j]+1, matrix[i][j-1]+1, matrix[i-1][j-1]+cost)
		}
	}
	return matrix[la][lb]
}

func min3(a, b, c int) int {
	m := a
	if b < m {
		m = b
	}
	if c < m {
		m = c
	}
	return m
}

func fuzzyRatio(a, b string) int {
	al, bl := strings.ToLower(a), strings.ToLower(b)
	maxLen := len(al)
	if len(bl) > maxLen {
		maxLen = len(bl)
	}
	if maxLen == 0 {
		return 100
	}
	dist := levenshtein(al, bl)
	return int(float64(maxLen-dist) / float64(maxLen) * 100)
}

func isSequel(t1, t2 string) bool {
	return fuzzyRatio(t1, t2) > 50
}

func recommend(films []Film, targetIdx int, dedupSequels bool) []Candidate {
	neighborIdxs := findNeighbors(films, targetIdx, 31)

	maxVotes := 0
	candidates := make([]Candidate, 0, len(neighborIdxs))
	for _, idx := range neighborIdxs {
		f := films[idx]
		if f.VoteCount > maxVotes {
			maxVotes = f.VoteCount
		}
		candidates = append(candidates, Candidate{
			Title: f.Title,
			Year:  f.Year,
			Score: f.VoteAverage,
			Votes: f.VoteCount,
			Index: idx,
		})
	}

	mainTitle := candidates[0].Title
	mainYear := float64(candidates[0].Year)

	for i := range candidates {
		c := &candidates[i]
		if isSequel(mainTitle, c.Title) {
			c.RankScore = 0
			continue
		}
		fact1 := 1.0
		if mainYear > 0 && c.Year > 0 {
			fact1 = gaussian(mainYear, float64(c.Year), 20)
		}
		fact2 := 0.0
		if maxVotes > 0 {
			fact2 = gaussian(float64(c.Votes), float64(maxVotes), float64(maxVotes))
		}
		c.RankScore = c.Score * c.Score * fact1 * fact2
	}

	for i := 0; i < len(candidates); i++ {
		for j := i + 1; j < len(candidates); j++ {
			if candidates[j].RankScore > candidates[i].RankScore {
				candidates[i], candidates[j] = candidates[j], candidates[i]
			}
		}
	}

	var selected []Candidate
	for _, c := range candidates {
		if len(selected) >= 5 {
			break
		}
		dominated := false
		for _, s := range selected {
			if s.Title == c.Title || isSequel(c.Title, s.Title) {
				dominated = true
				break
			}
		}
		if !dominated {
			selected = append(selected, c)
		}
	}

	if dedupSequels {
		removeSet := make(map[string]bool)
		for i, f1 := range selected {
			for j, f2 := range selected {
				if j <= i {
					continue
				}
				if isSequel(f1.Title, f2.Title) {
					if f1.Year < f2.Year {
						removeSet[f2.Title] = true
					} else {
						removeSet[f1.Title] = true
					}
				}
			}
		}
		filtered := make([]Candidate, 0)
		for _, s := range selected {
			if !removeSet[s.Title] {
				filtered = append(filtered, s)
			}
		}
		selected = filtered

		for _, c := range candidates {
			if len(selected) >= 5 {
				break
			}
			dominated := false
			for _, s := range selected {
				if s.Title == c.Title || isSequel(c.Title, s.Title) {
					dominated = true
					break
				}
			}
			if !dominated {
				selected = append(selected, c)
			}
		}
	}

	if len(selected) > 5 {
		selected = selected[:5]
	}
	return selected
}

func findByTitle(films []Film, query string) int {
	q := strings.ToLower(strings.TrimSpace(query))
	for i, f := range films {
		if strings.ToLower(strings.TrimSpace(f.Title)) == q {
			return i
		}
	}
	bestIdx, bestScore := -1, 0
	for i, f := range films {
		score := fuzzyRatio(q, strings.ToLower(f.Title))
		if score > bestScore {
			bestScore = score
			bestIdx = i
		}
	}
	if bestScore >= 60 {
		return bestIdx
	}
	return -1
}

func main() {
	movie := flag.String("movie", "", "Movie title to get recommendations for")
	id := flag.Int("id", -1, "Movie index in the dataset")
	noDedup := flag.Bool("no-dedup", false, "Disable sequel deduplication")
	dataDir := flag.String("data-dir", "data", "Path to CSV data directory")
	flag.Parse()

	if *movie == "" && *id < 0 {
		fmt.Println("Error: provide --movie or --id")
		os.Exit(1)
	}

	moviesPath := *dataDir + "/tmdb_5000_movies.csv"
	creditsPath := *dataDir + "/tmdb_5000_credits.csv"

	fmt.Println("Loading dataset...")
	movieRows, err := loadMovies(moviesPath)
	if err != nil {
		fmt.Printf("Error loading movies: %v\n", err)
		os.Exit(1)
	}
	creditRows, err := loadMovies(creditsPath)
	if err != nil {
		fmt.Printf("Error loading credits: %v\n", err)
		os.Exit(1)
	}

	creditMaps := make([]map[string]interface{}, len(creditRows))
	for i, r := range creditRows {
		creditMaps[i] = r
	}

	fmt.Println("Building film records...")
	films := buildFilms(movieRows, creditMaps)

	targetIdx := *id
	if *movie != "" {
		targetIdx = findByTitle(films, *movie)
		if targetIdx < 0 {
			fmt.Printf("Error: could not find movie matching '%s'\n", *movie)
			os.Exit(1)
		}
	}

	target := films[targetIdx]
	fmt.Printf("\nRecommendations for: %s (%d)\n", target.Title, target.Year)
	fmt.Println(strings.Repeat("=", 60))

	results := recommend(films, targetIdx, !*noDedup)
	for i, r := range results {
		yearStr := "?"
		if r.Year > 0 {
			yearStr = strconv.Itoa(r.Year)
		}
		fmt.Printf("  %d. %s (%s) — IMDB: %.1f\n", i+1, r.Title, yearStr, r.Score)
	}
}
