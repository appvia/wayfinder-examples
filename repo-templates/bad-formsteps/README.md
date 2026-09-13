# bad-formsteps

A deliberately broken template: its form step names an input that does not
exist, so registering it in Wayfinder fails the manifest sync. It exists for
the Wayfinder smoke suite, which asserts that failure and its error message.
Do not scaffold from it, and do not fix the spelling.
