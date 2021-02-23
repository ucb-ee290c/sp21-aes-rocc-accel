#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <string.h>
#include <unistd.h>
#include "openssl_aes.h"

char *default_keydata = "go bears lmao";
char *default_input[] = {"a", "abcd", "this is a test", "this is a bigger test", 
                         "\nWho are you?\nI am the 'Doctor'.\n'Doctor' who?\nPrecisely!", NULL};
unsigned int salt[] = {12345, 54321};  // random 8 bytes
char *usage = "Usage:\n"
              "  -h      = display this msg\n"
              "  -a      = accelerate\n"
              "  -k data = set keydata\n"
              "  -f file = provide input file\n";

int main(int argc, char *argv[])
{
  bool help = false;
  bool acc = false;
  char *keydata = NULL;
  char *filename = NULL;
  int opt;
  extern char *optarg;
  while ((opt = getopt(argc, argv, "hak:f:")) != -1) {
    switch (opt) {
      case 'h': help = true; break;
      case 'a': acc = true; break;
      case 'k': keydata = malloc(strlen(optarg) + 1); strcpy(keydata, optarg);
                  break;
      case 'f': filename = malloc(strlen(optarg) + 1); strcpy(filename, optarg);
                  break;
      default : fprintf(stderr, "%s", usage);
                  exit(1);
    }
  }

  if (help) {
    fprintf(stderr, "%s", usage);
    exit(1);
  }

  if (!keydata) {
    keydata = default_keydata;
  }

  char **input = calloc(2, 4);  // two NULL pointers
  FILE *fp;
  size_t len;  // unused
  if (filename) {
    if (!(fp = fopen(filename, "r"))) {
      fprintf(stderr, "bad filename\n");
      exit(1);
    }
    // getdelim assumes no 0 bytes ('\0') in file
    if (getdelim(input, &len, '\0', fp) == -1) {
      fprintf(stderr, "empty file\n");
      exit(1);
    } 
    input[0][strlen(input[0]) - 2] = '\0'; // don't ask 
  } else {
    input = default_input;
  }
 
  /* "opaque" encryption, decryption ctx structures that libcrypto uses to record
     status of enc/dec operations */
  EVP_CIPHER_CTX en, de;

  int i;
  int keydata_len = strlen(keydata);

  /* gen key and iv. init the cipher ctx object */
  if (aes_init((unsigned char *)keydata, keydata_len, (unsigned char *)&salt, &en, &de)) {
    fprintf(stderr, "couldn't initialize AES cipher\n");
    exit(1);
  }

  /* encrypt and decrypt each input string and compare with the original */
  for (i = 0; input[i]; i++) {
    char *plaintext;
    unsigned char *ciphertext;
    int olen, len;
    
    /* The enc/dec functions deal with binary data and not C strings. strlen() will 
       return length of the string without counting the '\0' string marker. We always
       pass in the marker byte to the encrypt/decrypt functions so that after decryption 
       we end up with a legal C string */
    olen = len = strlen(input[i])+1;
    
    ciphertext = aes_encrypt(&en, (unsigned char *)input[i], &len);
    plaintext = (char *)aes_decrypt(&de, ciphertext, &len);

    if (strncmp(plaintext, input[i], olen)) 
      printf("FAIL: enc/dec failed for \"%s\"\n", input[i]);
    else 
      printf("OK: enc/dec ok for \"%s\"\n", plaintext);
    
    free(ciphertext);
    free(plaintext);
  }

  EVP_CIPHER_CTX_cleanup(&en);
  EVP_CIPHER_CTX_cleanup(&de);

  return 0;
}
