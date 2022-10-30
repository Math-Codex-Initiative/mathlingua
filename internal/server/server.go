/*
 * Copyright 2022 Dominic Kramer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package server

import (
	"encoding/json"
	"fmt"
	"mathlingua/internal/frontend"
	"mathlingua/internal/frontend/phase1"
	"mathlingua/internal/frontend/phase2"
	"mathlingua/internal/frontend/phase3"
	"mathlingua/internal/frontend/phase4"
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"github.com/gorilla/mux"
)

func Start() {
	router := mux.NewRouter()
	router.HandleFunc("/api/paths", paths).Methods("GET")
	router.HandleFunc("/api/page", page).Methods("GET")

	if err := http.ListenAndServe(":8080", router); err != nil {
		fmt.Println(err.Error())
	}
}

type PathsResponse struct {
	Error string
	Paths []string
}

func paths(writer http.ResponseWriter, request *http.Request) {
	setJsonContentType(writer)

	paths := make([]string, 0)
	err := filepath.Walk(".", func(p string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}

		if !info.IsDir() && strings.HasSuffix(p, ".math") {
			paths = append(paths, p)
		}

		return nil
	})

	var resp PathsResponse
	if err != nil {
		resp = PathsResponse{
			Error: err.Error(),
		}
	} else {
		resp = PathsResponse{
			Paths: paths,
		}
	}

	writeResponse(writer, &resp)
}

type PageResponse struct {
	Error       string
	Diagnostics []frontend.Diagnostic
	Root        phase4.Root
}

func page(writer http.ResponseWriter, request *http.Request) {
	setJsonContentType(writer)
	path := request.URL.Query().Get("path")

	bytes, err := os.ReadFile(path)
	if err != nil {
		resp := PageResponse{
			Error: err.Error(),
		}
		writeResponse(writer, &resp)
		return
	}

	root, diagnostics := parse(string(bytes))

	resp := PageResponse{
		Diagnostics: diagnostics,
		Root:        root,
	}

	writeResponse(writer, &resp)
}

func setJsonContentType(writer http.ResponseWriter) {
	writer.Header().Set("Content-Type", "application/json")
}

func writeResponse[T any](writer http.ResponseWriter, resp *T) {
	if err := json.NewEncoder(writer).Encode(&resp); err != nil {
		fmt.Println(err)
		writer.WriteHeader(http.StatusInternalServerError)
	}
}

func parse(text string) (phase4.Root, []frontend.Diagnostic) {
	tracker := frontend.NewDiagnosticTracker()

	lexer1 := phase1.NewLexer(text, tracker)
	lexer2 := phase2.NewLexer(lexer1, tracker)
	lexer3 := phase3.NewLexer(lexer2, tracker)

	root := phase4.Parse(lexer3, tracker)

	return root, tracker.Diagnostics()
}
