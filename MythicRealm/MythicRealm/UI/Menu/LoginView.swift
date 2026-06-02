import SwiftUI

struct LoginView: View {
    @EnvironmentObject var gameState: GameState
    @State private var username: String = ""
    @State private var password: String = ""
    @State private var errorMessage: String = ""
    @State private var showRegister: Bool = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 24) {
                Spacer()

                // Title
                VStack(spacing: 6) {
                    Text("MYTHIC REALM")
                        .font(.system(size: 28, weight: .black, design: .serif))
                        .foregroundColor(.yellow)
                        .shadow(color: .orange, radius: 3)
                    Text("神域传说")
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.6))
                }

                Spacer().frame(height: 20)

                // Form
                VStack(spacing: 14) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("账号")
                            .font(.caption)
                            .foregroundColor(.gray)
                        TextField("输入账号", text: $username)
                            .textFieldStyle(.plain)
                            .padding(12)
                            .background(Color.white.opacity(0.08))
                            .cornerRadius(8)
                            .foregroundColor(.white)
                            .autocapitalization(.none)
                            .disableAutocorrection(true)
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text("密码")
                            .font(.caption)
                            .foregroundColor(.gray)
                        SecureField("输入密码", text: $password)
                            .textFieldStyle(.plain)
                            .padding(12)
                            .background(Color.white.opacity(0.08))
                            .cornerRadius(8)
                            .foregroundColor(.white)
                    }
                }
                .padding(.horizontal, 40)

                // Error
                if !errorMessage.isEmpty {
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundColor(.red)
                }

                // Login button
                Button(action: doLogin) {
                    Text("登录")
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(Color.green.opacity(0.7))
                        .cornerRadius(10)
                }
                .padding(.horizontal, 40)

                // Switch to register
                Button(action: { showRegister = true }) {
                    Text("没有账号？注册")
                        .font(.subheadline)
                        .foregroundColor(.cyan)
                }

                Spacer()
            }

            // Register overlay
            if showRegister {
                RegisterView(showRegister: $showRegister)
            }
        }
    }

    private func doLogin() {
        errorMessage = ""
        guard !username.isEmpty, !password.isEmpty else {
            errorMessage = "请输入账号和密码"
            return
        }
        do {
            try AccountManager.shared.login(username: username, password: password)
            afterAuth()
        } catch AccountManager.AuthError.invalidCredentials {
            errorMessage = "账号或密码错误"
        } catch {
            errorMessage = "登录失败"
        }
    }

    private func afterAuth() {
        if AccountManager.shared.hasCharacter() {
            AccountManager.shared.loadGameState(into: gameState)
            gameState.currentScreen = .home
        } else {
            gameState.currentScreen = .createCharacter
        }
    }
}

struct RegisterView: View {
    @EnvironmentObject var gameState: GameState
    @Binding var showRegister: Bool
    @State private var username: String = ""
    @State private var password: String = ""
    @State private var confirmPassword: String = ""
    @State private var errorMessage: String = ""

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 24) {
                Spacer()

                Text("注册新账号")
                    .font(.title2.bold())
                    .foregroundColor(.white)

                VStack(spacing: 14) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("账号（至少2个字符）")
                            .font(.caption)
                            .foregroundColor(.gray)
                        TextField("输入账号", text: $username)
                            .textFieldStyle(.plain)
                            .padding(12)
                            .background(Color.white.opacity(0.08))
                            .cornerRadius(8)
                            .foregroundColor(.white)
                            .autocapitalization(.none)
                            .disableAutocorrection(true)
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text("密码（至少4个字符）")
                            .font(.caption)
                            .foregroundColor(.gray)
                        SecureField("输入密码", text: $password)
                            .textFieldStyle(.plain)
                            .padding(12)
                            .background(Color.white.opacity(0.08))
                            .cornerRadius(8)
                            .foregroundColor(.white)
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text("确认密码")
                            .font(.caption)
                            .foregroundColor(.gray)
                        SecureField("再次输入密码", text: $confirmPassword)
                            .textFieldStyle(.plain)
                            .padding(12)
                            .background(Color.white.opacity(0.08))
                            .cornerRadius(8)
                            .foregroundColor(.white)
                    }
                }
                .padding(.horizontal, 40)

                if !errorMessage.isEmpty {
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundColor(.red)
                }

                Button(action: doRegister) {
                    Text("注册")
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(Color.blue.opacity(0.7))
                        .cornerRadius(10)
                }
                .padding(.horizontal, 40)

                Button(action: { showRegister = false }) {
                    Text("已有账号？返回登录")
                        .font(.subheadline)
                        .foregroundColor(.cyan)
                }

                Spacer()
            }
        }
    }

    private func doRegister() {
        errorMessage = ""
        guard password == confirmPassword else {
            errorMessage = "两次密码不一致"
            return
        }
        do {
            try AccountManager.shared.register(username: username, password: password)
            gameState.currentScreen = .createCharacter
            showRegister = false
        } catch AccountManager.AuthError.usernameExists {
            errorMessage = "账号已存在"
        } catch AccountManager.AuthError.usernameTooShort {
            errorMessage = "账号至少需要2个字符"
        } catch AccountManager.AuthError.passwordTooShort {
            errorMessage = "密码至少需要4个字符"
        } catch {
            errorMessage = "注册失败"
        }
    }
}
