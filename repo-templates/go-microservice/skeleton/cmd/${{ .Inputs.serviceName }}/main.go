// Command ${{ .Inputs.serviceName }} was scaffolded by Wayfinder for stack
// "${{ .Stack.Name }}" in repository ${{ .Repo.URL }}.
package main

import "fmt"

func main() {
	fmt.Println("hello from ${{ .Inputs.serviceName }} (stack ${{ .Stack.Name }})")
}
