CC=gcc
CFLAGS=-Wall

main: main.c openssl_aes.c
	$(CC) $(CFLAGS) -lcrypto main.c openssl_aes.c -o main

.PHONY: clean

clean: 
	rm -f main
