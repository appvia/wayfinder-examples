package ${{ .Inputs.packageName }}

import "testing"

func TestVersion(t *testing.T) {
	if Version == "" {
		t.Fatal("expected a version")
	}
}
