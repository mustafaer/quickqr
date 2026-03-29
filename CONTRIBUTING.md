# Contributing to QuickQR

Thank you for your interest in contributing! Here's how you can help.

## Getting Started

1. **Fork** the repository
2. **Clone** your fork:
   ```bash
   git clone https://github.com/<your-username>/quickqr.git
   cd quickqr
   ```
3. **Install** dependencies:
   ```bash
   npm install
   ```
4. **Create a branch** for your feature or fix:
   ```bash
   git checkout -b feature/your-feature-name
   ```

## Development

```bash
# Start dev server
npm start

# Run linter
npm run lint

# Build for production
npm run build

# Sync & run on Android
npx cap sync android
npx cap run android
```

## Pull Request Process

1. Make sure your code builds without errors (`npm run build`)
2. Run the linter and fix any issues (`npm run lint`)
3. Write clear, descriptive commit messages
4. Open a Pull Request against the `main` branch
5. Describe what your PR does and link any related issues

## Code Style

- Follow existing code conventions
- Use TypeScript strict mode
- Prefer `ChangeDetectionStrategy.OnPush` for components
- Register all Ionicons explicitly via `addIcons()`
- Keep components standalone (no NgModules)

## Reporting Bugs

Open an [issue](https://github.com/nicemustafa/quickqr/issues) with:
- Device model and Android version
- Steps to reproduce
- Expected vs actual behavior
- Screenshots if applicable

## Suggesting Features

Open an [issue](https://github.com/nicemustafa/quickqr/issues) with the `enhancement` label and describe:
- The problem you're trying to solve
- Your proposed solution
- Any alternatives you've considered

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).

