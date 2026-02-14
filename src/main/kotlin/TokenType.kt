package org.example

enum class TokenType {

    //Basic
    NUMBER,
    STRING,
    ID,

    VAR,
    PRINT,
    IF, ELSE,
    WHILE,


    PLUS, MINUS, STAR, SLASH,
    EQ, EQEQ, EXCL, NEQ,
    LT, GT, LTEQ, GTEQ,
    AND, OR,

    lPAREN, RPAREN,
    LBRACE, RBRACE,
    SEMICOLON,

    EOF
}