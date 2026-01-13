/** @type {import('jest').Config} */
export default {
  preset: 'ts-jest',
  testEnvironment: 'node',
  roots: ['<rootDir>/src'],
  testMatch: ['**/__tests__/**/*.test.ts', '**/*.test.ts'],
  collectCoverageFrom: [
    'src/**/*.ts',
    '!src/**/*.d.ts',
    '!src/**/index.ts',
    '!src/**/*.test.ts',
  ],
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
    '^@nimbus/shared$': '<rootDir>/../shared/src/index.ts',
    '^@nimbus/shared/(.*)$': '<rootDir>/../shared/src/$1',
    '@babylonjs/core': '<rootDir>/src/rendering/__mocks__/@babylonjs/core.ts',
  },
  transform: {
    '^.+\\.tsx?$': [
      'ts-jest',
      {
        tsconfig: {
          module: 'commonjs',
          moduleResolution: 'node',
          target: 'ES2022',
          lib: ['ES2022', 'DOM'],
          types: ['jest', 'node'],
          esModuleInterop: true,
          allowSyntheticDefaultImports: true,
        },
      },
    ],
  },
  transformIgnorePatterns: [
    'node_modules/(?!@nimbus/shared)',
  ],
  verbose: true,
};
