import SwiftUI

struct CreateCharacterView: View {
    @EnvironmentObject var gameState: GameState
    @State private var playerName: String = ""

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 0) {
                ScrollView(showsIndicators: false) {
                    VStack(spacing: 16) {
                        Text("创建角色")
                            .font(.title2.bold())
                            .foregroundColor(.white)

                        VStack(spacing: 6) {
                            Text("角色名称")
                                .font(.caption)
                                .foregroundColor(.gray)
                            TextField("输入名称", text: $playerName)
                                .textFieldStyle(.plain)
                                .padding(12)
                                .background(Color.white.opacity(0.08))
                                .cornerRadius(10)
                                .foregroundColor(.white)
                                .multilineTextAlignment(.center)
                        }
                        .padding(.horizontal, 40)

                        VStack(spacing: 4) {
                            Text("职业：战士")
                                .font(.headline)
                                .foregroundColor(.cyan)
                            Text("近战物理职业，高生命值和防御")
                                .font(.caption)
                                .foregroundColor(.gray)
                        }

                        VStack(spacing: 5) {
                            StatPreviewRow(label: "力量", value: 10)
                            StatPreviewRow(label: "敏捷", value: 5)
                            StatPreviewRow(label: "体质", value: 8)
                            StatPreviewRow(label: "智力", value: 3)
                            StatPreviewRow(label: "精神", value: 4)
                        }
                        .padding(16)
                        .background(Color.white.opacity(0.04))
                        .cornerRadius(12)
                        .padding(.horizontal, 40)
                    }
                    .padding(.top, 60)
                    .padding(.bottom, 20)
                }

                Button(action: createCharacter) {
                    Text("开始冒险")
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, minHeight: 46)
                        .background(Color.green.opacity(0.7))
                        .cornerRadius(12)
                }
                .padding(.horizontal, 40)
                .padding(.bottom, 40)
                .padding(.top, 10)
            }
        }
    }

    private func createCharacter() {
        let name = playerName.isEmpty ? "勇者" : playerName
        AccountManager.shared.createCharacter(name: name, gameState: gameState)
        gameState.currentScreen = .home
    }
}

struct StatPreviewRow: View {
    let label: String
    let value: Int

    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: 13))
                .foregroundColor(.gray)
                .frame(width: 40, alignment: .leading)
            Rectangle()
                .fill(Color.cyan.opacity(0.5))
                .frame(width: CGFloat(value) * 10, height: 8)
                .cornerRadius(2)
            Spacer()
            Text("\(value)")
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(.white)
        }
    }
}
