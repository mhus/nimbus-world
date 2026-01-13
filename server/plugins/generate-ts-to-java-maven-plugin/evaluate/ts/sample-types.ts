// Sample TypeScript declarations for evaluation tests

export interface Person {
  id: string;
  name?: string;
}

export interface Employee extends Person {
  employeeId: number;
}

export enum Role {
  USER = "USER",
  ADMIN = "ADMIN"
}

export class User implements Person {
  constructor(public id: string, public name?: string) {}
}

export type Identifier = string;
