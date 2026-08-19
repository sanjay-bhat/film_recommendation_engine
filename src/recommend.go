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

const collabWeight = 0.4

type Film struct {
	TmdbID       int
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

		tmdbID, _ := strconv.Atoi(fmt.Sprintf("%v", m["id"]))
		film := Film{
			TmdbID:       tmdbID,
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

func buildIdf(films []Film) map[string]float64 {
	nDocs := float64(len(films))
	docFreq := make(map[string]int)
	for _, f := range films {
		if f.PlotKeywords == "" {
			continue
		}
		seen := make(map[string]bool)
		for _, kw := range strings.Split(f.PlotKeywords, "|") {
			if kw != "" && !seen[kw] {
				docFreq[kw]++
				seen[kw] = true
			}
		}
	}
	idf := make(map[string]float64, len(docFreq))
	for token, df := range docFreq {
		idf[token] = math.Log(nDocs / float64(df))
	}
	return idf
}

func euclidean(a, b []float64) float64 {
	sum := 0.0
	for i := range a {
		d := a[i] - b[i]
		sum += d * d
	}
	return math.Sqrt(sum)
}

func findNeighbors(films []Film, targetIdx, n int, idf map[string]float64) []int {
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

	keywords := make(map[string]bool, len(idf))
	for k := range idf {
		keywords[k] = true
	}

	vectors := make([][]float64, len(films))
	for i, film := range films {
		filmFeats := make(map[string]bool)
		for _, f := range getFeatures(film) {
			filmFeats[f] = true
		}
		vec := make([]float64, len(featureList))
		for j, f := range featureList {
			if !filmFeats[f] {
				vec[j] = 0.0
			} else if keywords[f] {
				if w, ok := idf[f]; ok {
					vec[j] = w
				} else {
					vec[j] = 1.0
				}
			} else {
				vec[j] = 1.0
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

func findGenomeNeighbors(films []Film, targetIdx int, genomeFactors map[int][]float64, n int) []int {
	targetTmdb := films[targetIdx].TmdbID
	targetVec, ok := genomeFactors[targetTmdb]
	if !ok {
		return nil
	}

	type simPair struct {
		idx int
		sim float64
	}
	var sims []simPair
	for i, film := range films {
		if i == targetIdx {
			continue
		}
		vec, ok := genomeFactors[film.TmdbID]
		if !ok {
			continue
		}
		dot := 0.0
		for j := range targetVec {
			if j < len(vec) {
				dot += targetVec[j] * vec[j]
			}
		}
		if dot > 0 {
			sims = append(sims, simPair{i, dot})
		}
	}

	for i := 0; i < len(sims); i++ {
		for j := i + 1; j < len(sims); j++ {
			if sims[j].sim > sims[i].sim {
				sims[i], sims[j] = sims[j], sims[i]
			}
		}
	}

	result := make([]int, 0, n)
	for i := 0; i < n && i < len(sims); i++ {
		result = append(result, sims[i].idx)
	}
	return result
}

func loadItemFactors(path string) map[int][]float64 {
	factors := make(map[int][]float64)
	data, err := os.ReadFile(path)
	if err != nil {
		return factors
	}
	lines := strings.Split(string(data), "\n")
	for i, line := range lines {
		if i == 0 || strings.TrimSpace(line) == "" {
			continue
		}
		fields := strings.Split(line, ",")
		if len(fields) < 2 {
			continue
		}
		tmdbID, err := strconv.Atoi(fields[0])
		if err != nil {
			continue
		}
		vec := make([]float64, 0, len(fields)-1)
		for _, f := range fields[1:] {
			v, err := strconv.ParseFloat(f, 64)
			if err != nil {
				continue
			}
			vec = append(vec, v)
		}
		factors[tmdbID] = vec
	}
	return factors
}

func collabSimilarity(factors map[int][]float64, idA, idB int) float64 {
	va, okA := factors[idA]
	vb, okB := factors[idB]
	if !okA || !okB {
		return 0.0
	}
	dot := 0.0
	for i := range va {
		if i < len(vb) {
			dot += va[i] * vb[i]
		}
	}
	if dot < 0 {
		return 0.0
	}
	return dot
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

func recommend(films []Film, targetIdx int, dedupSequels bool,
	itemFactors, genomeFactors map[int][]float64, idf map[string]float64) []Candidate {
	targetTmdb := films[targetIdx].TmdbID

	// Stage 1: retrieve candidates from all three sources
	mergedSet := make(map[int]bool)
	for _, idx := range findNeighbors(films, targetIdx, 31, idf) {
		mergedSet[idx] = true
	}
	for _, idx := range findGenomeNeighbors(films, targetIdx, genomeFactors, 31) {
		mergedSet[idx] = true
	}
	targetVec, hasCollab := itemFactors[targetTmdb]
	if hasCollab {
		type simPair struct {
			idx int
			sim float64
		}
		var sims []simPair
		for i, film := range films {
			if i == targetIdx {
				continue
			}
			vec, ok := itemFactors[film.TmdbID]
			if !ok {
				continue
			}
			dot := 0.0
			for j := range targetVec {
				if j < len(vec) {
					dot += targetVec[j] * vec[j]
				}
			}
			if dot > 0 {
				sims = append(sims, simPair{i, dot})
			}
		}
		for i := 0; i < len(sims); i++ {
			for j := i + 1; j < len(sims); j++ {
				if sims[j].sim > sims[i].sim {
					sims[i], sims[j] = sims[j], sims[i]
				}
			}
		}
		for i := 0; i < 31 && i < len(sims); i++ {
			mergedSet[sims[i].idx] = true
		}
	}
	delete(mergedSet, targetIdx)

	// Stage 2: score the merged pool
	maxVotes := 0
	candidates := make([]Candidate, 0, len(mergedSet))
	for idx := range mergedSet {
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

	mainTitle := films[targetIdx].Title
	mainYear := float64(films[targetIdx].Year)

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
		contentScore := c.Score * c.Score * fact1 * fact2
		csim := collabSimilarity(itemFactors, targetTmdb, films[c.Index].TmdbID)
		collabScore := csim * fact1 * fact2
		c.RankScore = (1-collabWeight)*contentScore + collabWeight*collabScore
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
	dataDir := flag.String("data-dir", "dataset", "Path to CSV data directory")
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

	factorsPath := *dataDir + "/item_factors.csv"
	itemFactors := loadItemFactors(factorsPath)
	if len(itemFactors) > 0 {
		fmt.Printf("Loaded collaborative factors for %d movies\n", len(itemFactors))
	} else {
		fmt.Println("No collaborative factors found — using content-based only")
	}

	genomePath := *dataDir + "/genome_factors.csv"
	genomeFactors := loadItemFactors(genomePath)
	if len(genomeFactors) > 0 {
		fmt.Printf("Loaded genome factors for %d movies\n", len(genomeFactors))
	} else {
		fmt.Println("No genome factors found — using binary features for content")
	}

	fmt.Println("Computing TF-IDF keyword weights...")
	idf := buildIdf(films)
	fmt.Printf("  %d unique keywords weighted\n", len(idf))

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

	results := recommend(films, targetIdx, !*noDedup, itemFactors, genomeFactors, idf)
	for i, r := range results {
		yearStr := "?"
		if r.Year > 0 {
			yearStr = strconv.Itoa(r.Year)
		}
		fmt.Printf("  %d. %s (%s) — IMDB: %.1f\n", i+1, r.Title, yearStr, r.Score)
	}
}
