#include <stdio.h>
#include <string.h>

#include "openssl/aes.h"

char *text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";

int main() {
    printf("%s\n", text);
    printf("%d\n", strlen(text));
    // more for padding?
    char *dest = malloc(strlen(text) + 1); 
    //AES_cbc_encrypt();
    return 0;
}
